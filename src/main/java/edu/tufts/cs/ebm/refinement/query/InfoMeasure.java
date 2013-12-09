package edu.tufts.cs.ebm.refinement.query;

import edu.tufts.cs.ebm.util.MathUtil;

public class InfoMeasure {
  /** The number of true positives. */
  protected int truePositives;
  /** The number of retrieved documents. */
  protected int allRetrieved;
  /** The number of relevant documents. */
  protected int allRelevant;
  /** The precision. */
  protected double precision;
  /** The recall. */
  protected double recall;
  /** The f-measure. */
  protected double fmeasure;

  /**
   * Default constructor.
   * 
   * @param truePositives
   * @param allRetrieved
   * @param allRelevant
   */
  public InfoMeasure( int truePositives, int allRelevant ) {
    this.truePositives = truePositives;
    this.allRelevant = allRelevant;

    this.recall = calculateRecall( truePositives, allRelevant );
  }

  /**
   * Default constructor.
   * 
   * @param truePositives
   * @param allRetrieved
   * @param allRelevant
   */
  public InfoMeasure( int truePositives, int allRetrieved, int allRelevant ) {
    this.truePositives = truePositives;
    this.allRetrieved = allRetrieved;
    this.allRelevant = allRelevant;

    this.precision = calculatePrecision( truePositives, allRetrieved );
    this.recall = calculateRecall( truePositives, allRelevant );
    this.fmeasure = calculateFmeasure( this.precision, this.recall );
  }

  /**
   * Calculate the precision.
   * 
   * @param truePositives
   * @param allRetrieved
   * @return
   */
  public static double calculatePrecision( int truePositives, int allRetrieved ) {
    return (double) truePositives / (double) allRetrieved;
  }

  /**
   * Calculate the recall.
   * 
   * @param truePositives
   * @param allRelevant
   * @return
   */
  public static double calculateRecall( int truePositives, int allRelevant ) {
    return (double) truePositives / (double) allRelevant;
  }

  /**
   * Calculate the f-measure (harmonic mean).
   * 
   * @param precision
   * @param recall
   * @return
   */
  public static double calculateFmeasure( double precision, double recall ) {
    return ( 2 * precision * recall ) / ( precision + recall );
  }

  /**
   * Get the true positives.
   * 
   * @return
   */
  public int getTruePositives() {
    return this.truePositives;
  }

  /**
   * Get all retrieved.
   * 
   * @return
   */
  public int getAllRetrieved() {
    return this.allRetrieved;
  }

  /**
   * Get all relevant.
   * 
   * @return
   */
  public int getAllRelevant() {
    return this.allRelevant;
  }

  /**
   * Get the precision.
   * 
   * @return
   */
  public double getPrecision() {
    return this.precision;
  }

  /**
   * Get the recall.
   * 
   * @return
   */
  public double getRecall() {
    return this.recall;
  }

  /**
   * Get the f-measure.
   * 
   * @return
   */
  public double getFmeasure() {
    return this.fmeasure;
  }

  /**
   * Long-form String output.
   * 
   * @return
   */
  public String toStringVerbose() {
    return "\t Precision:" + MathUtil.round( precision, 4 ) + "\t Recall:"
        + MathUtil.round( recall, 4 ) + "\t F-measure: "
        + MathUtil.round( fmeasure, 4 );
  }

  @Override
  public String toString() {
    return "\t Recall:" + MathUtil.round( recall, 4 );
  }

}
