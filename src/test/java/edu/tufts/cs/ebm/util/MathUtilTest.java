package edu.tufts.cs.ebm.util;

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

public class MathUtilTest {

  /**
   * Test the linear random number generator.
   */
  @Test
  public void testHarmonicRandomNumber() {
    int n = 50; // at n = 50, about 25% of the documents should fall in cat. 0

    Map<Integer, Integer> freqMap = new HashMap<>();
    for ( int i = 0; i < 1000; i++ ) {
      int rand = MathUtil.harmonicRandom( n );
      if ( freqMap.containsKey( rand ) ) {
        freqMap.put( rand, freqMap.get( rand ) + 1 );
      } else {
        freqMap.put( rand, 1 );
      }
    }

    assert freqMap.get( 0 ) > freqMap.get( 1 );
    assert freqMap.get( 1 ) > freqMap.get( 2 );
    assert freqMap.get( 2 ) > freqMap.get( 3 ); // on down...
  }

  /**
   * Test the linear random number generator.
   */
  @Test
  public void testExponentialRandomNumber() {
    int n = 50; // at n = 50, about 25% of the documents should fall in cat. 0

    Map<Integer, Integer> freqMap = new HashMap<>();
    for ( int i = 0; i < 1000; i++ ) {
      int rand = MathUtil.exponentialRandom( n );
      if ( freqMap.containsKey( rand ) ) {
        freqMap.put( rand, freqMap.get( rand ) + 1 );
      } else {
        freqMap.put( rand, 1 );
      }
    }

    assert freqMap.get( 0 ) > freqMap.get( 1 );
    assert freqMap.get( 1 ) > freqMap.get( 2 );
    assert freqMap.get( 2 ) > freqMap.get( 3 ); // on down...
  }

  /**
   * Test entropy.
   */
  @Test
  public void testEntropy() {
    // Original entropy
    double e = MathUtil.calculateShannonEntropy( 222, 533 );
    System.out.println( "entropy(222, 533) = " + e );
    assert MathUtil.round( e, 3 ) == .874;

    // "Aged, 80 and over"
    e = MathUtil.calculateShannonEntropy( 30, 64 );
    System.out.println( "entropy(30, 64) = " + e );
    assert MathUtil.round( e, 3 ) == .903;

    e = MathUtil.calculateShannonEntropy( 192, 469 );
    System.out.println( "entropy(192, 469) = " + e );
    assert MathUtil.round( e, 3 ) == .869;

    // Individuality
    e = MathUtil.calculateShannonEntropy( 3, 0 );
    System.out.println( "entropy(3, 0) = " + e );
    assert MathUtil.round( e, 3 ) == 0;

    e = MathUtil.calculateShannonEntropy( 219, 533 );
    System.out.println( "entropy(219, 533) = " + e );
    assert MathUtil.round( e, 3 ) == .87;

    // Antigens, CD
    e = MathUtil.calculateShannonEntropy( 0, 8 );
    System.out.println( "entropy(0, 8) = " + e );
    assert MathUtil.round( e, 3 ) == 0;

    e = MathUtil.calculateShannonEntropy( 222, 525 );
    System.out.println( "entropy(222, 525) = " + e );
    assert MathUtil.round( e, 3 ) == .878;
  }

  /**
   * Test info gain.
   */
  @Test
  public void testInfoGain() {
    double e = MathUtil.calculateShannonEntropy( 222, 533 );

    // Aged, 80 and over
    double eSub1 = MathUtil.calculateShannonEntropy( 30, 64 );
    double eSub2 = MathUtil.calculateShannonEntropy( 192, 469 );

    double i = MathUtil.calculateInfoGain( e, eSub1, eSub2 );
    System.out.println( "infoGain(.874, .903, .869) = " + i );
    assert i == 0;

    // Individuality
    eSub1 = MathUtil.calculateShannonEntropy( 3, 0 );
    eSub2 = MathUtil.calculateShannonEntropy( 219, 533 );

    i = MathUtil.calculateInfoGain( e, eSub1, eSub2 );
    System.out.println( "infoGain(.874, 0, .87) = " + i );
    assert MathUtil.round( i, 3 ) == .439;

    // Antigens, CD
    eSub1 = MathUtil.calculateShannonEntropy( 0, 8 );
    eSub2 = MathUtil.calculateShannonEntropy( 222, 525 );

    i = MathUtil.calculateInfoGain( e, eSub1, eSub2 );
    System.out.println( "infoGain(.874, .878, 0) = " + i );
    assert MathUtil.round( i, 3 ) == .435;
  }
}
