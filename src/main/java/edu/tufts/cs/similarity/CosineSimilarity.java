package edu.tufts.cs.similarity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

import com.aliasi.spell.TfIdfDistance;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;

public class CosineSimilarity implements Runnable {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( CosineSimilarity.class );
  /** The number of times to weight the L2 papers (as compared to L1s). */
  protected static final int L2_WEIGHT = 1;
  /** The number of times to weight the L2 papers (as compared to L1s). */
  protected static final int L1_WEIGHT = 0;
  /** The data cache. */
  protected JCS cache;
  /** The title TF-IDF instance. */
  protected TfIdfDistance tfIdf;
  /** The first citation. */
  protected Citation c;
  /** The second citation. */
  protected Set<Citation> compareTo;
  /** The active review. */
  protected SystematicReview activeReview;

  /**
   * Default constructor.
   */
  public CosineSimilarity( JCS cache, TfIdfDistance tfIdf,
      Citation c, Set<Citation> compareTo,
      SystematicReview activeReview ) {
    this.cache = cache;
    this.tfIdf = tfIdf;
    this.c = c;
    this.compareTo = compareTo;
    this.activeReview = activeReview;
  }

  /**
   * Default constructor.
   */
  public CosineSimilarity( TfIdfDistance tfIdf, Citation c,
      Set<Citation> compareTo,
      SystematicReview activeReview ) {
    this.cache = null;
    this.tfIdf = tfIdf;
    this.c = c;
    this.compareTo = compareTo;
    this.activeReview = activeReview;
  }

  @Override
  public void run() {
    c.setSimilarity( calculateSimilarity() );
  }

  /**
   * Calculate the similarity between the citation and the review's seeds.
   *
   * @param c
   * @return
   */
  @SuppressWarnings( "unchecked" )
  public double calculateSimilarity() {
    if ( compareTo.isEmpty() ) {
      return 0.0;
    }

    String key = c.getPmid().toString();
    Map<PubmedId, Double> simMap = null;
    if ( cache != null ) {
      simMap = (Map<PubmedId, Double>) cache.get( key );
      try {
        cache.remove( key );
      } catch ( CacheException e ) {
        LOG.error( "Unable to remove from cache: " + key, e );
      }
    }
    if ( simMap == null ) {
      simMap = new HashMap<>();
    }

    double totalL2Sim = 0.0;
    double totalL1MinusL2Sim = 0.0;
    for ( Citation seed : compareTo ) {
      Double sim = simMap.get( seed.getPmid() );
      if ( sim == null ) {
        sim = tfIdf.proximity( c.getTitle() + " " +
          c.getAbstr() /*+ seed.getMeshTerms().toString()*/,
          seed.getTitle() + " " + seed.getAbstr() /*+
          seed.getMeshTerms().toString()*/ );

        if ( activeReview.getRelevantLevel2().contains( seed.getPmid() ) ) {
          sim = sim * L2_WEIGHT;
        } else {
          sim = sim * L1_WEIGHT;
        }

        simMap.put( seed.getPmid(), sim );
      }

      if ( activeReview.getRelevantLevel2().contains( seed.getPmid() ) ) {
        totalL2Sim += sim;
      } else {
        totalL1MinusL2Sim += sim;
      }
    }

    try {
      if ( cache != null ) cache.put( key, simMap );
    } catch ( CacheException e ) {
      LOG.error( "Unable to cache: " + e );
    }

    int numL2s = 0;
    for ( Citation seed : compareTo ) {
      if ( activeReview.getRelevantLevel2().contains( seed.getPmid() ) ) {
        numL2s++;
      }
    }

//    double divisor = L1_WEIGHT * (compareTo.size()-numL2s) * L2_WEIGHT*numL2s;
//    double totalSim = ( totalL2Sim + totalL1MinusL2Sim ) / divisor;
    double avgl2Sim = totalL2Sim / (numL2s*L2_WEIGHT);
    double avgl1MinusL2Sim = totalL1MinusL2Sim /
        ( compareTo.size() - numL2s )*L1_WEIGHT;
    double totalSim = avgl2Sim - avgl1MinusL2Sim;

    return totalSim;
  }

}
