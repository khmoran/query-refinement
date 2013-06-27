package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.Classifier;
import edu.tufts.cs.ml.classify.NaiveBayesClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;

/**
 * An online simulation of a systematic review using an LDA representation and
 * a naive bayes classifier.
 */
public class OnlineSimulatorLdaNaiveBayes extends OnlineSimulatorLda {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulatorLdaNaiveBayes.class );

  /**
   * Default constructor.
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorLdaNaiveBayes( String review ) throws Exception {
    super( review );
  }

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

  @Override
  protected void initializeClassifier( Set<Citation> citations ) {
    // this will happen in rank(...) method
  }
  
  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  @Override
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, FeatureVector<Integer>> citations,
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
      if ( ufv.getClassification().equals( POS ) ) val = cert;
      else val = -1*cert;
      rankMap.put( val, pmid );
    }

    return rankMap;
  }
}
