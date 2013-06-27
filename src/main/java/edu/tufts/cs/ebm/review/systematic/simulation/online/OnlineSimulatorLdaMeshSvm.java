package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.text.ParseException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ca.uwo.csd.ai.nlp.kernel.LinearKernel;
import ca.uwo.csd.ai.nlp.mallet.libsvm.SVMClassifierTrainer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;

/**
 * An online simulation of a systematic review using an LDA representation and
 * a naive bayes classifier.
 */
public class OnlineSimulatorLdaMeshSvm extends OnlineSimulatorLdaMesh {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulatorLdaMeshSvm.class );
  /** The instances. */
  protected InstanceList instances;

  /**
   * Default constructor.
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorLdaMeshSvm( String review ) throws Exception {
    super( review );
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

    if ( instances == null ) instances = initializeMallet( citations.values() );

    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();
    // can't classify w/o samples from each class

    if ( !( expertRelevantPapers.isEmpty() || expertIrrelevantPapers.isEmpty() ) ) {
      InstanceList training = createTrainingData( instances,
          expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

      SVMClassifierTrainer trainer = new SVMClassifierTrainer( new LinearKernel(), true );
      trainer.train( training );
      
      trainer.getClassifier().classify( instances );
      for ( Instance i : instances ) {
        try {
          PubmedId pmid = new PubmedId( i.getName().toString() );
    
          Integer label = Integer.valueOf( i.getLabeling().getBestLabel().toString() );
    
          double cert;
          if ( i.getLabeling().numLocations() > 1 ) {
            cert = i.getLabeling().getValueAtRank( 0 ) - i.getLabeling().getValueAtRank( 1 );
          } else {
            cert = i.getLabeling().getValueAtRank( 0 );
          }
    
          double val = cert;
          if ( label.equals( NEG ) ) val = -1*cert;
    
          rankMap.put( val, pmid );
        } catch ( NumberFormatException | ParseException e ) {
          LOG.error( e );
        }
      }
    } else { // essentially random
      for ( PubmedId c : citations.keySet() ) {
        rankMap.put( 0.0, c );
      }
    }

    return rankMap;
  }
}
