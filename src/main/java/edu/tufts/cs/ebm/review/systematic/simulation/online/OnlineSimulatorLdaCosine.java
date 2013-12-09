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
import edu.tufts.cs.ml.text.CosineSimilarity;
import edu.tufts.cs.similarity.CachedCosineSimilarity;

/**
 * An online simulation of a systematic review using an LDA representation and a
 * naive bayes classifier.
 */
public class OnlineSimulatorLdaCosine extends OnlineSimulatorLda {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorLdaCosine.class );
  /** The Cosine Similarity. */
  protected CosineSimilarity<Integer> cs;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorLdaCosine( String review ) throws Exception {
    super( review );
  }

  /**
   * Create a training data set from the LDA features.
   * 
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
    TrainRelation<Integer> relation = new TrainRelation<Integer>( "lda", m );
    for ( FeatureVector<Integer> fv : expertRelevantPapers ) {
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<>( POS,
          fv.getId() );
      lfv.putAll( fv );
      relation.add( lfv );
    }
    for ( FeatureVector<Integer> fv : expertIrrelevantPapers ) {
      LabeledFeatureVector<Integer> lfv = new LabeledFeatureVector<>( NEG,
          fv.getId() );
      lfv.putAll( fv );
      relation.add( lfv );
    }

    return relation;
  }

  @Override
  protected void initializeClassifier( Set<Citation> citations ) {
    // initialize the cosine similarity
    cs = new CachedCosineSimilarity<Integer>( defaultCache,
        new TrainRelation<Integer>( "", new Metadata() ) );
  }

  /**
   * Rank the query results using cosine similarity.
   * 
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
      FeatureVector<Integer> ufv = citations.get( pmid );
      double sim = cs.calculateSimilarity( ufv );
      rankMap.put( sim, pmid );
    }

    // record the ranks
    recordRank( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );

    return rankMap;
  }
}
