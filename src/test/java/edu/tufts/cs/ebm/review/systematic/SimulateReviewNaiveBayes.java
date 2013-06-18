package edu.tufts.cs.ebm.review.systematic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.Classifier;
import edu.tufts.cs.ml.classify.NaiveBayesClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;
import edu.tufts.cs.ml.text.BagOfWords;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewNaiveBayes extends SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewNaiveBayes.class );

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, UnlabeledFeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {

    // train the bag of words
    for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
      bow.train( fv, POS );
    }
    for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
      bow.train( fv, NEG );
    }
    
    // put the classified Citations into a training set
    TrainRelation<Integer> relation = bow.getTrainingData();
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
   * Initialize the classifier.
   */
  protected void initializeClassifier( Set<Citation> citations ) {
    // initialize the bag of words
    bow = new BagOfWords<Integer>(
        new File( "src/main/resources/stoplists/en.txt" ) );
    for ( Citation c : activeReview.getSeedCitations() ) {
      // seed citations are in the positive class
      bow.train( c.getPmid().toString(), c.getTitle() + " " + c.getAbstr(), 1 );
    }
  
    // create the features
    List<String> texts = new ArrayList<String>();
    for ( Citation c : citations ) {
      texts.add( c.getTitle() + " " + c.getAbstr() );
    }
    bow.createFeatures( texts );
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
