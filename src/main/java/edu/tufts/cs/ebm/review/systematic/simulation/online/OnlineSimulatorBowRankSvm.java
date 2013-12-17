package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
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
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OnlineSimulatorBowRankSvm extends OnlineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorBowRankSvm.class );
  /** The ensemble size (number of classifigers) for bagging. */
  protected static final int ENSEMBLE_SIZE = 10;
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
  public OnlineSimulatorBowRankSvm( String review ) throws Exception {
    super( review );
  }

  /**
   * Set the SVM parameter c.
   * 
   * @param c
   */
  public void setC( double c ) {
    this.cParam = c;
    LOG.info( "c: " + c );
  }

  /**
   * Get the papers terms to propose.
   * 
   * @param query
   * @return
   */
  @Override
  protected Set<PubmedId> getPaperProposals(
      TreeMultimap<Double, PubmedId> rankMap,
      Set<PubmedId> expertRelevantPapers, Set<PubmedId> expertIrrelevantPapers ) {
    Set<PubmedId> results = new HashSet<>();

    List<PubmedId> citList = new ArrayList<>();
    for ( Double sim : rankMap.keySet() ) {
      for ( PubmedId pmid : rankMap.get( sim ) ) {
        if ( !expertRelevantPapers.contains( pmid )
            && !expertIrrelevantPapers.contains( pmid ) ) {
          citList.add( pmid );
        }
      }
    }

    LOG.info( "Getting deterministic paper proposal set..." );
    // TODO temporarily removing stochastic element
    int lastIdx = ( citList.size() < PAPER_PROPOSALS_PER_ITERATION ) ? citList
        .size() : PAPER_PROPOSALS_PER_ITERATION;
    results.addAll( citList.subList( 0, lastIdx ) );

    LOG.info( "Paper proposals: " + results );
    return results;
  }

  /**
   * Initialize the classifier.
   */
  @Override
  protected void initializeClassifier( Collection<Citation> citations ) {
    // this will all happen in the rank(...) method
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
        fv.setRank( POS );
        minorityMap.put( fv, POS );
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

  /**
   * Do an ensemble ranking with undersampling and bagging, merged by the Borda
   * algorithm.
   * 
   * @param minorityClass
   * @param majorityClass
   * @param test
   * @return
   */
  protected TreeMultimap<Double, PubmedId> ensembleRank(
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

    TreeMultimap<Double, PubmedId> ranking = TreeMultimap.create();
    for ( int i = 0; i < merged.size(); i++ ) {
      ranking.put( (double) i, merged.get( i ) );
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

    // undersampling the majority class
    // add majority instances
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
