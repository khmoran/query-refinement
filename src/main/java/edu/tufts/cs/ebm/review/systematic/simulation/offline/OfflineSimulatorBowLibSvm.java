package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.LibSvmClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;

/**
 * An offline simulation of a systematic review.
 */
public class OfflineSimulatorBowLibSvm extends OfflineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OfflineSimulatorBowLibSvm.class );

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OfflineSimulatorBowLibSvm( String review ) throws Exception {
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

    // add pseudo-documents
    for ( FeatureVector<Integer> fv : labeledTerms.keySet() ) {
      pos.add( fv ); // TODO fix this to handle negative pseudo documents
    }

    // can't classify w/o samples from each class
    if ( !( pos.isEmpty() || neg.isEmpty() ) ) {
      // create the training data, which is the expert-identified relevant
      // and irrelevant sets
      TrainRelation<Integer> trainRelation = new TrainRelation<Integer>(
          "train", bow.getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> fv : pos ) {
        LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<Integer>(
            POS, fv.getId() );
        lfv.putAll( fv );
        trainRelation.add( lfv );
      }
      for ( FeatureVector<Integer> fv : neg ) {
        LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<Integer>(
            NEG, fv.getId() );
        lfv.putAll( fv );
        trainRelation.add( lfv );
      }

      // create the test set
      TestRelation<Integer> testRelation = new TestRelation<Integer>( "test",
          bow.getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : test.values() ) {
        testRelation.add( (UnlabeledFeatureVector<Integer>) c );
      }

      LibSvmClassifier c = new LibSvmClassifier();
      c.train( trainRelation );
      try {
        TreeMultimap<Double, FeatureVector<Integer>> results = c
            .rank( testRelation );

        for ( Double rank : results.keySet() ) {
          for ( FeatureVector<Integer> fv : results.get( rank ) ) {
            try {
              PubmedId pmid = edu.tufts.cs.ebm.util.Util
                  .createOrUpdatePmid( Long.valueOf( fv.getId() ) );
              ranking.add( pmid );
            } catch ( NumberFormatException e ) {
              LOG.error( "Could not parse pmid: " + fv.getId(), e );
            }
          }
        }
      } catch ( IncomparableFeatureVectorException e ) {
        LOG.error( "Could not compare feature vectors.", e );
      }
    }

    System.out.println( "Ranking: " + ranking.size() );
    return ranking;
  }

}
