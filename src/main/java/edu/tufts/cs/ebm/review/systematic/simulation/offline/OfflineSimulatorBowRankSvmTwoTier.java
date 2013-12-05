package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.util.Util;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OfflineSimulatorBowRankSvmTwoTier extends OfflineSimulatorBowRankSvm {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OfflineSimulatorBowRankSvmTwoTier.class );
  /** The positive class label for L2. */
  protected static final int POS_L2 = 1;
  /** The positive class label for L1. */
  protected static final int POS_L1 = 2;
  /** The negative class label. */
  protected static final int NEG = 3;

  /**
   * Default constructor.
   * @param review
   * @throws Exception
   */
  public OfflineSimulatorBowRankSvmTwoTier( String review ) throws Exception {
    super( review );
  }

  @Override
  protected List<PubmedId> rank(
      Map<PubmedId, FeatureVector<Integer>> training,
      Map<PubmedId, FeatureVector<Integer>> test ) {
    List<PubmedId> ranking = new ArrayList<PubmedId>();
    
    List<FeatureVector<Integer>> pos = new ArrayList<FeatureVector<Integer>>();
    List<FeatureVector<Integer>> neg = new ArrayList<FeatureVector<Integer>>();
    
    for ( PubmedId id : training.keySet() ) {
      if ( activeReview.getRelevantLevel1().contains( id )
        || activeReview.getRelevantLevel2().contains( id ) ) {
        pos.add( training.get( id ) );
      } else {
        neg.add( training.get( id ) );
      }
    }

    // can't classify w/o samples from each class
    if ( !( pos.isEmpty() || neg.isEmpty() ) ) {
      // create the training data, which is the expert-identified relevant
      // and irrelevant sets
      Map<FeatureVector<Integer>, Integer> minorityMap =
          new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : pos ) {
        fv.setQid( 1 );
        PubmedId pmid = Util.createOrUpdatePmid( Long.valueOf( fv.getId() ) );
        int posRank = POS_L1;
        if ( activeReview.getRelevantLevel2().contains( pmid ) ) posRank = POS_L2;
        fv.setRank( posRank );
        minorityMap.put( fv, posRank );
      }
      Map<FeatureVector<Integer>, Integer> majorityMap =
          new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : neg ) {
        fv.setQid( 1 );
        fv.setRank( NEG );
        majorityMap.put( fv, NEG );
      }

      // create the test set
      TestRelation<Integer> testRelation = new TestRelation<Integer>(
          "test", bow.getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : test.values() ) {
        if ( c.getQid() == null ) c.setQid( 1 );
        testRelation.add( (UnlabeledFeatureVector<Integer>) c );
      }
      
      ranking = ensembleRank( minorityMap, majorityMap, testRelation );
    }
    
    return ranking;
  }
}
