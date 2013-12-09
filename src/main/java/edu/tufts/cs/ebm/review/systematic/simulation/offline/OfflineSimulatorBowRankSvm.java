package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.RankSvmClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;
import edu.tufts.cs.rank.BordaAggregator;

/**
 * An offline simulation of a systematic review.
 */
public class OfflineSimulatorBowRankSvm extends OfflineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OfflineSimulatorBowRankSvm.class );
  /** The ensemble size (number of classifiers) for bagging. */
  protected static final int ENSEMBLE_SIZE = 5;
  /**
   * The number of times the size of the minority class to sample. NOTE: can
   * make this number very high to eliminate undersampling.
   */
  protected static final int UNDERSAMPLING_MULTIPLIER = 1;
  /** The positive class label for L2. */
  protected static final int POS = 1;
  /** The negative class label. */
  protected static final int NEG = 2;
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
  public OfflineSimulatorBowRankSvm( String review ) throws Exception {
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
      Map<FeatureVector<Integer>, Integer> minorityMap = new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : pos ) {
        fv.setQid( 1 );
        fv.setRank( POS );
        minorityMap.put( fv, POS );
      }
      Map<FeatureVector<Integer>, Integer> majorityMap = new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : neg ) {
        fv.setQid( 1 );
        fv.setRank( NEG );
        majorityMap.put( fv, NEG );
      }

      // create the test set
      TestRelation<Integer> testRelation = new TestRelation<Integer>( "test",
          bow.getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : test.values() ) {
        if ( c.getQid() == null )
          c.setQid( 1 );
        testRelation.add( (UnlabeledFeatureVector<Integer>) c );
      }

      ranking = ensembleRank( minorityMap, majorityMap, testRelation );
    }

    return ranking;
  }

  /**
   * Do an ensemble ranking with undersampling and bagging, merged by the Borda
   * algorithm.
   * 
   * @param minorityClass
   * @param majorityClass
   * @param test
   * @return
   */
  protected List<PubmedId> ensembleRank(
      Map<FeatureVector<Integer>, Integer> minorityClass,
      Map<FeatureVector<Integer>, Integer> majorityClass,
      TestRelation<Integer> test ) {

    LOG.info( "Ensemble ranking..." );
    List<List<PubmedId>> rankings = new ArrayList<List<PubmedId>>();

    // there will be no undersampling if the majority class is smaller than
    // the minority class, so only rank once
    int ensembleSize = ENSEMBLE_SIZE;
    if ( minorityClass.size() * UNDERSAMPLING_MULTIPLIER >= majorityClass
        .size() ) {
      ensembleSize = 1;
    }

    for ( int i = 1; i <= ensembleSize; i++ ) {
      LOG.info( "\tRanking ensemble #" + i );
      rankings.add( bag( minorityClass, majorityClass, test ) );
    }

    BordaAggregator<PubmedId> borda = new BordaAggregator<PubmedId>();
    List<PubmedId> merged = borda.aggregate( rankings );

    List<PubmedId> ranking = new ArrayList<PubmedId>();
    for ( int i = 0; i < merged.size(); i++ ) {
      ranking.add( merged.get( i ) );
    }

    return ranking;
  }

  /**
   * Bag and run the classifier on an undersampled subset.
   *
   * @param minorityClass
   * @param majorityClass
   * @param test
   * @return
   */
  protected List<PubmedId> bag(
      Map<FeatureVector<Integer>, Integer> minorityClass,
      Map<FeatureVector<Integer>, Integer> majorityClass,
      TestRelation<Integer> test ) {
    // prepare the data for the ranking function
    TrainRelation<Integer> trainRelation = new TrainRelation<Integer>( "train",
        bow.getTrainingData().getMetadata() );
    // add minority instances
    for ( FeatureVector<Integer> fv : minorityClass.keySet() ) {
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<Integer>(
          minorityClass.get( fv ), fv.getId() );
      lfv.setQid( fv.getQid() );
      lfv.setRank( fv.getRank() );
      lfv.putAll( fv );
      trainRelation.add( lfv );
    }

    // undersampling the majority class
    // add majority instances
    int numNegSamples = ( minorityClass.size() * UNDERSAMPLING_MULTIPLIER >= majorityClass
        .size() ) ? majorityClass.size() : minorityClass.size()
        * UNDERSAMPLING_MULTIPLIER;
    ArrayList<FeatureVector<Integer>> shuffled = new ArrayList<>(
        majorityClass.keySet() );
    Collections.shuffle( shuffled );
    for ( int i = 0; i < numNegSamples; i++ ) {
      FeatureVector<Integer> fv = shuffled.get( i );
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<Integer>(
          majorityClass.get( fv ), fv.getId() );
      lfv.setQid( fv.getQid() );
      lfv.setRank( fv.getRank() );
      lfv.putAll( fv );
      trainRelation.add( lfv );
    }

    LOG.info( "\t\t# minority instances: " + minorityClass.size() + "\n\t\t"
        + "# majority instances: " + numNegSamples + "/" + majorityClass.size() );
    List<PubmedId> ranking = new ArrayList<PubmedId>();
    RankSvmClassifier c = new RankSvmClassifier( cParam );
    c.train( trainRelation );
    try {
      TreeMultimap<Double, FeatureVector<Integer>> results = c.rank( test );

      for ( Double rank : results.keySet() ) {
        for ( FeatureVector<Integer> fv : results.get( rank ) ) {
          try {
            PubmedId pmid = edu.tufts.cs.ebm.util.Util.createOrUpdatePmid( Long
                .valueOf( fv.getId() ) );
            ranking.add( pmid );
          } catch ( NumberFormatException e ) {
            LOG.error( "Could not parse pmid: " + fv.getId(), e );
          }
        }
      }
    } catch ( IncomparableFeatureVectorException e ) {
      LOG.error( "Could not compare feature vectors.", e );
    }

    return ranking;
  }

}
