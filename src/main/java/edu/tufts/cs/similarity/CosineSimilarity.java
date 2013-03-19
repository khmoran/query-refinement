package edu.tufts.cs.similarity;

import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

import com.aliasi.spell.TfIdfDistance;

import edu.tufts.cs.ebm.review.systematic.Citation;

public class CosineSimilarity implements Runnable {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( CosineSimilarity.class );
  /** The data cache. */
  protected JCS cache;
  /** The title TF-IDF instance. */
  protected TfIdfDistance titleTfIdf;
  /** The abstract TF-IDF instance. */
  protected TfIdfDistance abstrTfIdf;
  /** The first citation. */
  protected Citation c;
  /** The second citation. */
  protected Set<Citation> compareTo;

  /**
   * Default constructor.
   */
  public CosineSimilarity( JCS cache, TfIdfDistance titleTfIdf,
      TfIdfDistance abstrTfIdf, Citation c, Set<Citation> compareTo ) {
    this.cache = cache;
    this.titleTfIdf = titleTfIdf;
    this.abstrTfIdf = abstrTfIdf;
    this.c = c;
    this.compareTo = compareTo;
  }


  /**
   * Default constructor.
   */
  public CosineSimilarity( TfIdfDistance titleTfIdf,
      TfIdfDistance abstrTfIdf, Citation c, Set<Citation> compareTo ) {
    this.cache = null;
    this.titleTfIdf = titleTfIdf;
    this.abstrTfIdf = abstrTfIdf;
    this.c = c;
    this.compareTo = compareTo;
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
  public double calculateSimilarity() {
    if ( compareTo.isEmpty() ) {
      return 0.0;
    }

    // get the similarity scores
    double totalSim = 0.0;
    for ( Citation seed : compareTo ) {
      // try to find it in the cache first
      String key = c.getPmid().toString() + ":" + seed.getPmid().toString();
      Double avg = null;
      if ( cache != null ) avg = (Double) cache.get( key );
      if ( avg == null ) {
        double titleSim = titleTfIdf.proximity( c.getTitle(), seed.getTitle() );
        double abstractSim = abstrTfIdf.proximity(
            c.getAbstr(), seed.getAbstr() );
        avg = ( titleSim + abstractSim )/2;

        try {
          if ( cache != null ) cache.put( key, avg );
        } catch ( CacheException e ) {
          LOG.error( "Unable to cache: " + e );
        }
      }
      totalSim += avg;
    }

    double avgSim = totalSim / (double) compareTo.size();

    return avgSim;
  }

}
