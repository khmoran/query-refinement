package edu.tufts.cs.ebm.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;
import com.google.common.primitives.Ints;

import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;

public class MathUtil {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      MathUtil.class );
  /** The random number generator. */
  protected static final Random RND = new Random();

  /**
   * Private constructor for utility class.
   */
  private MathUtil() {
    // purposely not instantiable
  }

  /**
   * Get k unique random integers according to the 1/r distribution
   * from 0..n.
   *
   * @param n
   * @param k
   * @return
   */
  public static Set<Integer> uniqueHarmonicRandom( int n, int k ) {
    Set<Integer> indices = new HashSet<>();

    int maxSize = Math.min( n, k );
    while ( indices.size() < maxSize ) {
      indices.add( harmonicRandom( n ) );
    }

    return indices;
  }

  /**
   * Get k unique random integers according to the 1/r^2 distribution
   * from 0..n.
   *
   * @param n
   * @param k
   * @return
   */
  public static Set<Integer> uniqueQuadraticRandom( int n, int k ) {
    Set<Integer> indices = new HashSet<>();

    int maxSize = Math.min( n, k );
    while ( indices.size() < maxSize ) {
      indices.add( exponentialRandom( n ) );
    }

    return indices;
  }

  /**
   * Generate a random number from 0..n according to a 1/r distribution.
   * @param n
   * @return
   */
  public static int harmonicRandom( int n ) {
    double w = 0;
    double[] weights = new double[n];
    for ( int i = 1; i <= n; i++ ) {
      w += 1/(double) i;
      weights[i-1] = w;
    }

    Random r = new Random();
    double d = r.nextDouble() * w;

    for ( int i = 0; i < n; i++ ) {
      double wt = weights[i];
      if ( d < wt ) {
        return i;
      }
    }

    return 0;
  }

  /**
   * Generate a random number from 0..n according to a 1/r^2 distribution.
   * @param n
   * @return
   */
  public static int exponentialRandom( int n ) {
    double w = 0;
    double[] weights = new double[n];
    for ( int i = 1; i <= n; i++ ) {
      w += 1/(double) ( i*i );
      weights[i-1] = w;
    }

    Random r = new Random();
    double d = r.nextDouble() * w;

    for ( int i = 0; i < n; i++ ) {
      double wt = weights[i];
      if ( d < wt ) {
        return i;
      }
    }

    return 0;
  }

  /**
   * Return real number uniformly in [0, 1).
   */
  protected static double uniform() {
    return Math.random();
  }

  /**
   * Calculate entropy with a collection of class instances.
   * @param values
   * @return
   */
  public static Double calculateShannonEntropy( Collection<String> values ) {
    Map<String, Integer> map = new HashMap<String, Integer>();
    // count the occurrences of each value
    for ( String sequence : values ) {
      if ( !map.containsKey( sequence ) ) {
        map.put( sequence, 0 );
      }
      map.put( sequence, map.get( sequence ) + 1 );
    }

    // calculate the entropy
    Double result = 0.0;
    for ( String sequence : map.keySet() ) {
      Double frequency = (double) map.get( sequence ) / values.size();
      result -= frequency * ( Math.log( frequency ) / Math.log( 2 ) );
    }

    return result;
  }

  /**
   * Calculate entropy with the frequency values for each class.
   * @param freqMap
   * @return
   */
  public static Double calculateShannonEntropy( List<Integer> frequencies ) {
    int[] freqs = Ints.toArray( frequencies );
    return calculateShannonEntropy( freqs );
  }

  /**
   * Calculate entropy with the frequency values for each class.
   * @param freqMap
   * @return
   */
  public static Double calculateShannonEntropy( int... frequencies ) {
    int totalVals = 0;

    for ( int freq : frequencies ) {
      totalVals += freq;
    }

    // calculate the entropy
    Double result = 0.0;
    for ( int freq : frequencies ) {
      double frequency = (double) freq / totalVals;
      double numerator = frequency * Math.log( frequency );
      if ( Double.isNaN( numerator ) ) numerator = 0;
      result -= numerator / Math.log( 2 );
    }

    return result;
  }

  /**
   * Calculate the information gain.
   * @param entropyT
   * @param entropyTA
   * @return
   */
  public static double calculateInfoGain( double entropyT,
      double... entropyTsubs ) {
    String eq = String.valueOf( MathUtil.round( entropyT, 5 ) );

    double total = entropyT;
    for ( double entropyTsub : entropyTsubs ) {
      double term = ( (double) ( 1.0/entropyTsubs.length ) * entropyTsub );
      total = total - term;
      eq += " - (1/" + entropyTsubs.length + ")(" +
          MathUtil.round( entropyTsub, 5 ) + ")";
    }

    eq += " = " + total;
    LOG.trace( eq );

    return total > 0 ? total : 0;
  }

  /**
   * Round the double to the provided number of places.
   *
   * @param value
   * @param numPlaces
   * @return
   */
  public static double round( double value, int numPlaces ) {
    double multFactor = Math.pow( 10, numPlaces );
    double zeroDPs = value * multFactor;
    return Math.round( zeroDPs ) / multFactor;
  }


  /**
   * Get a random sample of size m from items.
   * @param items
   * @param m
   * @return
   */
  public static <T> Set<T> randomSample( List<T> items, int m ) {
    HashSet<T> res = new HashSet<T>( m );
    int n = items.size();
    for ( int i = n - m; i < n; i++ ) {
      int pos = RND.nextInt( i + 1 );
      T item = items.get( pos );
      if ( res.contains( item ) )
        res.add( items.get( i ) );
      else
        res.add( item );
    }
    return res;
  }

  /**
   * Calculate the median of a collection of doubles.
   *
   * @param vals
   * @return
   */
  public static double calcMedian( Collection<Double> vals ) {
    List<Double> sorted = new ArrayList<Double>();
    sorted.addAll( vals );
    Collections.sort( sorted );

    if ( vals.size() % 2 == 1 ) {
      return sorted.get( ( vals.size() + 1 ) / 2 - 1 );
    } else {
      double lower = sorted.get( vals.size() / 2 - 1 );
      double upper = sorted.get( vals.size() / 2 );

      return ( lower + upper ) / 2.0;
    }
  }

  /**
   * Calculate the mean of a collection of doubles.
   *
   * @param vals
   * @return
   */
  public static double calcMean( Collection<Double> vals ) {
    double total = 0;
    long numCounted = 0;

    for ( Double d : vals ) {
      total += d;
      numCounted++;
    }

    return total / numCounted;
  }

  /**
   * Calculate the covariance.
   *
   * @param x
   * @param y
   * @return
   */
  public static double calcCovariance( List<Double> x, List<Double> y ) {
    double meanX = calcMean( x );
    double meanY = calcMean( y );

    List<Double> mults = new ArrayList<Double>();
    for ( int i = 0; i < x.size(); i++ ) {
      mults.add( x.get( i ) * y.get( i ) );
    }

    double meanXY = calcMean( mults );
    double meanXmeanY = meanX * meanY;

    return meanXY - meanXmeanY;
  }

  /**
   * Calculate the sample standard deviation for the given feature.
   *
   * @param featureName
   * @return
   */
  public static double calcStandardDeviation( Collection<Double> vals ) {
    List<Double> sqDeviations = new ArrayList<Double>();
    double mean = calcMean( vals );

    for ( Double d : vals ) {
      double deviation = d - mean;
      sqDeviations.add( deviation * deviation );
    }

    double total = 0;
    for ( double d : sqDeviations ) {
      total += d;
    }

    double result = total / ( vals.size() - 1 );
    return Math.sqrt( result );
  }

  /**
   * Calculate the Pearson Correlation Coefficient.
   * @param x
   * @param y
   * @return
   * @throws IncomparableFeatureVectorException
   */
  public static double calcPCC( List<Double> x, List<Double> y )
    throws ArithmeticException {
    if ( x.size() != y.size() ) {
      throw new ArithmeticException(
          "The vectors are different sizes." );
    }
    double n = x.size();

    double sum_sq_x = 0;
    double sum_sq_y = 0;
    double sum_coproduct = 0;
    double mean_x = 0;
    double mean_y = 0;

    for ( int i = 0; i < n; i++ ) {
      double xi = x.get( i );
      double yi = y.get( i );
      sum_sq_x += xi * xi;
      sum_sq_y += yi * yi;
      sum_coproduct += xi * yi;
      mean_x += xi;
      mean_y += yi;
    }

    mean_x = mean_x / n;
    mean_y = mean_y / n;
    double pop_sd_x = Math.sqrt( ( sum_sq_x / n ) - ( mean_x * mean_x ) );
    double pop_sd_y = Math.sqrt( ( sum_sq_y / n ) - ( mean_y * mean_y ) );
    double cov_x_y = ( sum_coproduct / n ) - ( mean_x * mean_y );
    double correlation = cov_x_y / ( pop_sd_x * pop_sd_y );
    return correlation;
  }

  /**
   * Calculate the z-score for the value given the sample.
   * @param sample
   * @param example
   * @return
   */
  public static double calcZscore( Collection<Double> sample,
      Double rawScore ) {
    double sampleMean = calcMean( sample );
    double sampleSd = calcStandardDeviation( sample );
    double zscore = ( rawScore - sampleMean )/sampleSd;
    return zscore;
  }

  /**
   * Normalize the values from [0, 1].
   * @param infoGains
   * @return
   */
  public static TreeMultimap<Double, String> normalize(
      TreeMultimap<Double, String> data ) {
    Double mean = MathUtil.calcMean( data.keySet() );
    Double sampleStdDev = MathUtil.calcStandardDeviation( data.keySet() );

    TreeMultimap<Double, String> normalized = TreeMultimap.create();
    for ( Double d : data.keySet() ) {
      Set<String> values = data.get( d );
      Double zscore = ( d - mean )/sampleStdDev;
      normalized.putAll( zscore, values );
    }

    return normalized;
  }
}
