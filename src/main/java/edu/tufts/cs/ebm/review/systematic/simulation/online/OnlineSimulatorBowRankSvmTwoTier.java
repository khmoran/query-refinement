package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.util.Util;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OnlineSimulatorBowRankSvmTwoTier extends OnlineSimulatorBowRankSvm {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorBowRankSvmTwoTier.class );
  /** The positive class label for L2. */
  protected static final int POS_L2 = 1;
  /** The positive class label for L1. */
  protected static final int POS_L1 = 2;
  /** The negative class label. */
  protected static final int NEG = 3;
  /** The default c parameter for SVM. */
  protected static final double DEFAULT_C = 1;
  /** The c parameter for SVM. */
  protected double cParam = DEFAULT_C;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBowRankSvmTwoTier( String review ) throws Exception {
    super( review );
  }

  @Override
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, FeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {

    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();

    // can't classify w/o samples from each class
    if ( !( expertRelevantPapers.isEmpty() || expertIrrelevantPapers.isEmpty() ) ) {
      // create the training data, which is the expert-identified relevant
      // and irrelevant sets
      Map<FeatureVector<Integer>, Integer> minorityMap = new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
        fv.setQid( 1 );
        PubmedId pmid = Util.createOrUpdatePmid( Long.valueOf( fv.getId() ) );
        int pos = POS_L1;
        if ( activeReview.getRelevantLevel2().contains( pmid ) )
          pos = POS_L2;
        fv.setRank( pos );
        minorityMap.put( fv, pos );
      }
      Map<FeatureVector<Integer>, Integer> majorityMap = new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
        fv.setQid( 1 );
        fv.setRank( NEG );
        majorityMap.put( fv, NEG );
      }

      // create the test set
      TestRelation<Integer> test = new TestRelation<Integer>( "test", bow
          .getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : citations.values() ) {
        if ( c.getQid() == null )
          c.setQid( 1 );
        test.add( (UnlabeledFeatureVector<Integer>) c );
      }

      rankMap = ensembleRank( minorityMap, majorityMap, test );
    } else { // essentially random
      for ( PubmedId c : citations.keySet() ) {
        rankMap.put( 0.0, c );
      }
    }
    return rankMap;
  }
}
