package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.ArrayList;
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
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.LibSvmClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OnlineSimulatorBowLibSvm extends OnlineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorBowLibSvm.class );

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBowLibSvm( String review ) throws Exception {
    super( review );
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
  protected void initializeClassifier( Set<Citation> citations ) {
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
      // and irrelevant sets// train the bag of words
      for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
        bow.train( fv, POS );
      }
      for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
        bow.train( fv, NEG );
      }

      // create the test set
      TestRelation<Integer> test = new TestRelation<Integer>( "test", bow
          .getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : citations.values() ) {
        test.add( (UnlabeledFeatureVector<Integer>) c );
      }

      LibSvmClassifier c = new LibSvmClassifier();
      c.train( bow.getTrainingData() );
      try {
        TreeMultimap<Double, FeatureVector<Integer>> results = c.rank( test );

        for ( Double rank : results.keySet().descendingSet() ) {
          for ( FeatureVector<Integer> fv : results.get( rank ) ) {
            try {
              PubmedId pmid = edu.tufts.cs.ebm.util.Util
                  .createOrUpdatePmid( Long.valueOf( fv.getId() ) );
              rankMap.put( rank, pmid );
            } catch ( NumberFormatException e ) {
              LOG.error( "Could not parse pmid: " + fv.getId(), e );
            }
          }
        }
      } catch ( IncomparableFeatureVectorException e ) {
        LOG.error( "Could not compare feature vectors.", e );
      }
    } else { // essentially random
      for ( PubmedId pmid : citations.keySet() ) {
        rankMap.put( 0.0, pmid );
      }
    }

    return rankMap;

  }
}
