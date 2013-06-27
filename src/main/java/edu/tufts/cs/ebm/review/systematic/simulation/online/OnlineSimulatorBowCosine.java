package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.text.CosineSimilarity;
import edu.tufts.cs.similarity.CachedCosineSimilarity;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and a cosine similarity classifier.
 */
public class OnlineSimulatorBowCosine extends OnlineSimulatorBow {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulatorBowCosine.class );
  /** The Cosine Similarity. */
  protected CosineSimilarity<Integer> cs;

  /**
   * Default constructor.
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBowCosine( String review ) throws Exception {
    super( review );
  }

  /**
   * Initialize the classifier.
   */
  protected void initializeClassifier( Set<Citation> citations ) {
    // initialize the cosine similarity
    cs = new CachedCosineSimilarity<Integer>( defaultCache, bow.getTrainingData() );
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, FeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {
    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();

    // train the bag of words
    for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
      bow.train( fv, POS );
    }
    for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
      bow.train( fv, NEG );
    }

    // "train" the cosine similarity with the relevant data
    cs.setCompareTo( bow.getTrainingData( POS ) );

    // test the remaining citations
    for ( PubmedId pmid : citations.keySet() ) {
      FeatureVector<Integer> ufv = citations.get( pmid );
      double sim = cs.calculateSimilarity( ufv );
      rankMap.put( sim, pmid );
    }

    return rankMap;
  }
}
