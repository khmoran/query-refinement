package edu.tufts.cs.rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.TreeMultimap;

public class BordaAggregator<E extends Comparable<E>> {
  private class DoubleAccumulator {
    /** The value. */
    public double val = 0.0;
  }

  /**
   * Aggregate the <n> Multimaps into one ranked list.
   * @param ranks
   * @return
   */
  @SuppressWarnings( "unchecked" )
  public <F extends Comparable<F>> List<E> aggregate(
      TreeMultimap<F, E>... ranks ) {
    List<List<E>> matrix = new ArrayList<>();

    for ( TreeMultimap<F, E> r : ranks ) {
      List<E> lst = new ArrayList<>();

      for ( F sim : r.keySet().descendingSet() ) {
        lst.addAll( r.get( sim ) );
      }

      matrix.add( lst );
    }

    return aggregate( matrix );
  }


  /**
   * Aggregate the <n> Lists into one ranked list.
   * @param ranks
   * @return
   */
  @SafeVarargs
  public final List<E> aggregate( List<E>... ranks ) {
    List<List<E>> matrix = new ArrayList<>();

    for ( List<E> r : ranks ) {
      matrix.add( r );
    }

    return aggregate( matrix );
  }


  /**
   * Aggregate the <n> arrays into one ranked list.
   * @param ranks
   * @return
   */
  @SafeVarargs
  public final List<E> aggregate( E[]... ranks ) {
    List<List<E>> matrix = new ArrayList<>();

    for ( E[] e : ranks ) {
      matrix.add( Arrays.asList( e ) );
    }

    return aggregate( matrix );
  }

  /**
   * Aggregate the n rankings.
   *
   * @param rankings
   * @return
   */
  protected List<E> aggregate( List<List<E>> rankings ) {
    Map<E, DoubleAccumulator> accumulatorMap = new HashMap<>();

    for ( int i = 0; i < rankings.size(); i++ ) {
      for ( int j = 0; j < rankings.get( i ).size(); j++ ) {
        DoubleAccumulator acc = accumulatorMap.get(
            rankings.get( i ).get( j ) );
        if ( acc == null ) {
          acc = new DoubleAccumulator();
          accumulatorMap.put( rankings.get( i ).get( j ), acc );
        }

        // pos 1 - 1.0
        // pos 2 - 0.5 = 1/2
        // pos n - 1/n
        acc.val += 1.0 / (double) ( j + 1 );
      }
    }

    List<Entry<E, DoubleAccumulator>> list = new LinkedList<>(
        accumulatorMap.entrySet() );
    Collections.sort( list, new Comparator<Entry<E, DoubleAccumulator>>() {
      @Override
      public int compare( Entry<E, DoubleAccumulator> o1,
          Entry<E, DoubleAccumulator> o2 ) {
        // descending
        return (int) Math.signum( o2.getValue().val - o1.getValue().val );
      }
    } );

    // calc max len
    int max = rankings.get( 0 ).size();

    for ( int i = 1; i < rankings.size(); i++ ) {
      if ( rankings.get( i ).size() > max )
        max = rankings.get( i ).size();
    }

    int n = Math.min( max, list.size() );

    List<E> aggRank = new ArrayList<>();
    for ( int i = 0; i < n; i++ ) {
      aggRank.add( list.get( i ).getKey() );
    }

    return aggRank;
  }
}
