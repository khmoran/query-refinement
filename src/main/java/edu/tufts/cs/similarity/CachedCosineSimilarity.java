package edu.tufts.cs.similarity;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.text.CosineSimilarity;

public class CachedCosineSimilarity<E> extends CosineSimilarity<E> {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( CachedCosineSimilarity.class );
  /** The data cache. */
  protected JCS cache;

  /**
   * Default constructor.
   */
  public CachedCosineSimilarity( JCS cache, TrainRelation<E> compareTo ) {
    super( compareTo );
    this.cache = cache;
    this.compareTo = compareTo;
  }

  /**
   * Default constructor.
   */
  public CachedCosineSimilarity( TrainRelation<E> compareTo ) {
    super( compareTo );
  }

  /**
   * Calculate the similarity between the citation and the review's seeds.
   *
   * @param c
   * @return
   */
  @Override
  @SuppressWarnings( "unchecked" )
  public double calculateSimilarity( UnlabeledFeatureVector<E> ufv ) {
    if ( compareTo.isEmpty() ) {
      return 0.0;
    }

    String key = ufv.getId();
    Map<String, Double> simMap = null;
    if ( cache != null ) {
      simMap = (Map<String, Double>) cache.get( key );
      try {
        cache.remove( key );
      } catch ( CacheException e ) {
        LOG.error( "Unable to remove from cache: " + key, e );
      }
    }
    if ( simMap == null ) {
      simMap = new HashMap<>();
    }

    double sum = 0.0;
    for ( LabeledFeatureVector<E> lfv : compareTo ) {
      Double sim = simMap.get( lfv.getId() );
      if ( sim == null ) {
        sim = ( ufv.dot( lfv ) /
                ufv.magnitude() * lfv.magnitude() );
        simMap.put( lfv.getId(), sim );
        sum += sim;
      } else {
        sum += sim;
      }
    }

    try {
      if ( cache != null ) cache.put( key, simMap );
    } catch ( CacheException e ) {
      LOG.error( "Unable to cache: " + e );
    }

    double avg = sum / (double) compareTo.size();

    return avg;
  }

}
