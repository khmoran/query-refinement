/**
 * CommandLineOptions.java
 */
package edu.tufts.cs.ebm.review.systematic.simulation.drivers;

import edu.tufts.cs.ml.exception.CommandLineArgumentException;


/**
 * A class containing the various command-line options for the composite-match
 * program.
 *
 * These options can also be set in a properties.xml file.
 */
public class SimulateReviewArguments {
  /*
   * Usage statements for command-line use.
   */
  /** The argument name for the dataset identifier. */
  public static final String ARG_DATASET = "dataset";
  /** The argument name for whether the simulation is online or offline. */
  public static final String ARG_IS_ONLINE = "online";
  /** The argument name for the representation type (bag of words, LDA...). */
  public static final String ARG_REPRESENTATION = "representation";
  /** The argument name for the classifier (cosine sim., SVM...). */
  public static final String ARG_CLASSIFIER = "classifier";
  /** The argument name for the classifier (cosine sim., SVM...). */
  public static final String ARG_HYPERPARAMETER = "hyperparameter";
  /** The usage message for the dataset identifier. */
  public static final String USAGE_DATASET = "The name of the dataset to use" +
  		" (ex. 'clopidogrel' or 'protonbeam').";
  /** The usage message for whether the simulation is online or offline. */
  public static final String USAGE_IS_ONLINE = "Whether the simulation is " +
  		"online (ex. 'true' or 'false').";
  /** The usage message for the representation type. */
  public static final String USAGE_REPRESENTATION = "The representation for " +
  		"the simulation data (ex. 'Bow' or 'Lda').";
  /** The usage message for the classifier. */
  public static final String USAGE_CLASSIFIER = "The type of classifier to " +
  		"use (ex. 'Cosine', 'LibSvm', 'SvmLight', or 'RankSvm').";
  /** The usage message for the classifier. */
  public static final String USAGE_HYPERPARAMETER = "The hyperparameter " +
      "value (ex. 'c' for rank-svm).";
  /** The usage message. */
  protected static String usage = "simulate <" + ARG_DATASET + "> <" +
    ARG_IS_ONLINE + "> <" + ARG_REPRESENTATION + "> <" + ARG_CLASSIFIER +
    ">\n\n<" + ARG_DATASET + ">:\t\t" + USAGE_DATASET + "\n<" + ARG_IS_ONLINE +
    ">:\t\t" + USAGE_IS_ONLINE + "\n<" + ARG_REPRESENTATION + ">:\t" +
    USAGE_REPRESENTATION + "\n<" + ARG_CLASSIFIER + ">:\t\t" + USAGE_CLASSIFIER
    + "\n\n[" + ARG_HYPERPARAMETER + "]:\t\t" + USAGE_HYPERPARAMETER;

  /*
   * Argument definitions for command line use.
   */
  /** The dataset identifier. */
  private String dataset;
  /** Whether the simulation is online or offline. */
  private boolean isOnline;
  /** The data representation format. */
  private String representation;
  /** The classifier to use on the data. */
  private String classifier;
  /** The hyperparameter. */
  private Double hyperparameter = null;

  /**
   * Options from the command line arguments override default settings
   * defined in this class.
   */
  public SimulateReviewArguments( String[] args )
    throws CommandLineArgumentException {
    if ( args.length >= 1 && args[0].toUpperCase().contains( "USAGE" ) ) {
      printUsage( "Usage:" );
    } else if ( args.length < 2 ) {
      printUsage( CommandLineArgumentException.DIFF_NUM_ARGS );
    }

    this.dataset = args[0];
    this.isOnline = Boolean.valueOf( args[1] );
    this.representation = args[2];
    this.classifier = args[3];
    
    if ( args.length > 4 ) {
      this.hyperparameter = Double.valueOf( args[4] );
    }
  }

  /**
   * Print the usage and the error message.
   * @param args
   * @throws CommandLineArgumentException
   */
  public void printUsage( String... args )
    throws CommandLineArgumentException {
    System.err.println( usage );

    if ( args.length > 0 && args[0].toUpperCase().contains( "USAGE" ) ) {
      System.exit( 0 );
    }
    if ( args.length == 1 ) {
      throw new CommandLineArgumentException( args[0] );
    } else {
      throw new CommandLineArgumentException( args[0], args[1] );
    }
  }

  /**
   * The dataset to use.
   *
   * @return
   */
  public String getDataset() {
    return this.dataset;
  }

  /**
   * Whether the simulation is online.
   *
   * @return
   */
  public boolean isOnline() {
    return this.isOnline;
  }

  /**
   * The representation of the data.
   *
   * @return
   */
  public String getRepresentation() {
    return this.representation;
  }

  /**
   * The classifier to use on the data.
   * @return
   */
  public String getClassifier() {
    return this.classifier;
  }

  /**
   * The hyperparameter.
   * @return
   */
  public Double getHyperparameter() {
    return this.hyperparameter;
  }
}
