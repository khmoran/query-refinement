package edu.tufts.cs.ebm.review.systematic;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.similarity.CachedCosineSimilarity;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewLDACosineSim extends SimulateReviewLDA {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewLDACosineSim.class );

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
    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();

    // "train" the cosine similarity with the relevant data
    TrainRelation<Integer> subRel = new TrainRelation<Integer>( "pos",
        (Metadata) relation.getMetadata().clone() );
    for ( LabeledFeatureVector<Integer> lfv : relation ) {
      if ( lfv.getLabel().equals( POS ) ) {
        subRel.add( lfv );
      }
    }
    cs.setCompareTo( subRel );

    // test the remaining citations
    for ( PubmedId pmid : citations.keySet() ) {
      UnlabeledFeatureVector<Integer> ufv = citations.get( pmid ); 
      double sim = cs.calculateSimilarity( ufv );
      rankMap.put( sim, pmid );
    }

    // record the ranks
    recordRank( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );

    return rankMap;
  }
  
  @Override
  protected void initializeClassifier( Set<Citation> citations ) {
    super.initializeClassifier( citations );
    
    // initialize the cosine similarity
    cs = new CachedCosineSimilarity<Integer>( defaultCache, new TrainRelation<Integer>( "", new Metadata() ) );
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
