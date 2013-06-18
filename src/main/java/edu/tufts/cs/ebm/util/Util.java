package edu.tufts.cs.ebm.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    Citation c = Ebean.find( Citation.class, id.longValue() );

    return c;
  }

  /**
   * Strip the punctuation, capitalization from the given String.
   * @param s
   * @return
   */
  public static String alphaNormalize( String s ) {
    return s.trim().replaceAll( "[^a-zA-Z]", "" ).toLowerCase();
  }

  /**
   * Strip the punctuation, capitalization from the given String.
   * @param s
   * @return
   */
  public static String normalize( String s ) {
    return s.trim().replaceAll( "[^a-zA-Z0-9]", "" ).toLowerCase();
  }

  /**
   * Strip the punctuation, capitalization from the given String.
   * @param s
   * @return
   */
  public static String removeFormatting( String s ) {
    return s.trim().replaceAll( "[^a-zA-Z0-9]", "" );
  }

  /**
   * Tokenize the input text.
   *
   * @param text
   * @return
   */
  public static List<String> tokenize( String text ) {
    List<String> tokens = new ArrayList<>();

    // split on strings, commas, semicolons, newlines, and tabs
    String regexp = "[\\/\\-\\s,;\\n\\t]+";
    String[] strTokens = text.split( regexp );

    for ( int i = 0; i < strTokens.length; i++ ) {
      String strToken = strTokens[i];
      String stripped = Util.removeFormatting( strToken );

      if ( !stripped.isEmpty() && !stripped.matches( ".*\\d.*" ) ) {
        tokens.add( stripped );
      }
    }

    return tokens;
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
