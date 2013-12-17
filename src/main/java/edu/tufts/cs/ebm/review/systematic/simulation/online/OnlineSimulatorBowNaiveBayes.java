package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cc.mallet.types.InstanceList;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.Classifier;
import edu.tufts.cs.ml.classify.NaiveBayesClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OnlineSimulatorBowNaiveBayes extends OnlineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorBowNaiveBayes.class );
  /** The instances. */
  protected InstanceList instances;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBowNaiveBayes( String review ) throws Exception {
    super( review );
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
      FeatureVector<Integer> fv = citations.get( pmid );
      UnlabeledFeatureVector<Integer> ufv;
      if ( fv instanceof UnlabeledFeatureVector ) {
        ufv = (UnlabeledFeatureVector<Integer>) fv;
      } else {
        continue;
      }
      double val = 0.0;
      double cert = 0.0;
      try {
        cl.classify( ufv );
        cert = cl.getCertainty( ufv );
      } catch ( IncomparableFeatureVectorException e ) {
        LOG.error( e );
      }
      if ( ufv.getClassification().equals( POS ) )
        val = cert;
      else
        val = -1 * cert;
      rankMap.put( val, pmid );
    }

    return rankMap;
  }
}
