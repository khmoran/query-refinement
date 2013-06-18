package edu.tufts.cs.ebm.review.systematic;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ml.DoubleFeature;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.Classifier;
import edu.tufts.cs.ml.classify.NaiveBayesClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;
import edu.tufts.cs.ml.topics.lda.BasicLDA;
import edu.tufts.cs.ml.topics.lda.LDA;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewLDA extends SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewLDA.class );
  /** The expected number of topics. */
  public static final int NUM_TOPICS = 20;
  /** The alpha sum prior. */
  public static final double ALPHA_SUM_PRIOR = 10;
  /** The beta prior. */
  public static final double BETA_PRIOR = 0.01;
  /** The number of LDA iterations. */
  public static final int NUM_LDA_IT = 2000;
  /** The LDA model. */
  protected LDA lda;

  /**
   * Create a training data set from the LDA features.
   * @param lda
   * @param expertRelevantPapers
   * @param expertIrrelevantPapers
   * @return
   */
  protected TrainRelation<Integer> createTrainingData(
      Collection<FeatureVector<Integer>> expertRelevantPapers,
      Collection<FeatureVector<Integer>> expertIrrelevantPapers ) {
    Metadata m = new Metadata();
    for ( int i = 0; i < NUM_TOPICS; i++ ) {
      m.put( String.valueOf( i ), "numeric" );
    }
    TrainRelation<Integer> relation = new TrainRelation<Integer>(
        "lda", m );
    for ( FeatureVector<Integer> fv : expertRelevantPapers ) {
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<>( POS, fv.getId() );
      lfv.putAll( fv );
      relation.add( lfv );
    }
    for ( FeatureVector<Integer> fv : expertIrrelevantPapers ) {
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<>( NEG, fv.getId() );
      lfv.putAll( fv );
      relation.add( lfv );
    }

    return relation;
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  @Override
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, UnlabeledFeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {

    // put the classified Citations into a training set
    TrainRelation<Integer> relation = createTrainingData(
        expertRelevantPapers.values(), expertIrrelevantPapers.values() );
    // classify the unclassified Citations using the model
    Classifier<Integer> cl = new NaiveBayesClassifier<Integer>();

    cl.train( relation );

    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();
    for ( PubmedId pmid : citations.keySet() ) {
      UnlabeledFeatureVector<Integer> ufv = citations.get( pmid );
      double val = 0.0;
      double cert = 0.0;
      try {
        cl.classify( ufv );
        cert = cl.getCertainty( ufv );
      } catch ( IncomparableFeatureVectorException e ) {
        LOG.error( e );
      }
      if ( ufv.getClassification().equals( POS ) ) val = cert;
      else val = -1*cert;
      rankMap.put( val, pmid );
    }

    // record the ranks
    recordRank( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );

    return rankMap;
  }
  
  @Override
  protected Map<PubmedId, UnlabeledFeatureVector<Integer>> createFeatureVectors(
      Set<Citation> citations ) {
    // create the feature vectors
    Map<PubmedId, UnlabeledFeatureVector<Integer>> fvs = new HashMap<>();
    for ( Citation c : citations ) {
      fvs.put( c.getPmid(), createUnlabeledVector( c ) );
    }
    
    return fvs;
  }

  /**
   * Create a FeatureVector from the LDA data.
   * @param c
   * @param relevant
   * @return
   */
  protected UnlabeledFeatureVector<Integer> createUnlabeledVector(
      Citation c ) {
    UnlabeledFeatureVector<Integer> fv = new UnlabeledFeatureVector<Integer>(
        c.getPmid().toString() );
    Multimap<Double, Integer> topicDist = lda.getTopics(
        c.getPmid().toString() );
    for ( double pct : topicDist.keySet() ) {
      for ( int topic : topicDist.get( pct ) ) {
        String str = String.valueOf( topic );
        fv.put( str, new DoubleFeature( str, pct ) );
      }
    }

    return fv;
  }

  
  @Override
  protected void initializeClassifier( Set<Citation> citations ) {
    StringBuilder sb = new StringBuilder();

    for ( Citation c : citations ) {
      sb.append( c.getPmid() + "\tX\t\"" + c.getTitle() + " " + c.getAbstr() +
          "\"\n" );
    }

    this.lda = new BasicLDA( NUM_TOPICS, ALPHA_SUM_PRIOR, BETA_PRIOR );
    try {
      lda.train( sb.toString(), NUM_LDA_IT );
    } catch ( IOException e ) {
      LOG.error( e );
    }
  }

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  public void simulateReview()
    throws InterruptedException, IOException {
    super.simulateReview();
  }

  /**
   * Tear down the test harness.
   * @throws IOException
   * @throws WriteException
   */
  @AfterSuite
  public void tearDown() throws IOException {
    super.tearDown();
  }
}
