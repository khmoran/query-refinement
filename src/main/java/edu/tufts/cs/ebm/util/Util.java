package edu.tufts.cs.ebm.util;

import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.avaje.ebean.Ebean;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;

public class Util {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( Util.class );

  /**
   * Private constructor for utility class.
   */
  protected Util() {
    // purposely not instantiable
  }

  /**
   * Create or update the PMID with this value.
   * @param l
   * @return
   */
  public static PubmedId createOrUpdatePmid( long l ) {
    PubmedId pmid = Ebean.find( PubmedId.class, l );
    if ( pmid == null ) {
      try {
        pmid = new PubmedId( l );
        Ebean.save( pmid );
      } catch ( NumberFormatException e ) {
        //LOG.warn( "Invalid PubMed id: " + l, e );
      }
    } else {
      Ebean.update( pmid );
    }

    return pmid;
  }

  /**
   * Get the citation.
   * @param id
   * @return
   */
  public static Citation getCitation( PubmedId id ) {
    return Ebean.find( Citation.class, id.longValue() );
  }

  /**
   * Sort the map by its values.
   *
   * @param map
   * @return
   */
  public static <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>>
  sortByEntries( Map<K, V> map ) {
    SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<Map.Entry<K, V>>(
        new Comparator<Map.Entry<K, V>>() {
          @Override
          public int compare( Map.Entry<K, V> e1, Map.Entry<K, V> e2 ) {
            int res = e2.getValue().compareTo( e1.getValue() );
            return res != 0 ? res : 1; // Special fix to preserve items with
                                       // equal values
          }
        } );
    sortedEntries.addAll( map.entrySet() );
    return sortedEntries;
  }
}
