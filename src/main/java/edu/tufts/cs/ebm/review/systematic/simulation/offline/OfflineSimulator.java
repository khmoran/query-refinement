package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.review.systematic.simulation.Simulator;
import edu.tufts.cs.ml.util.Util;

/**
 * An offline simulation of a systematic review.
 */
public abstract class OfflineSimulator<I, C> extends Simulator {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( OfflineSimulator.class );
  /** The active review. */
  protected SystematicReview activeReview;
  /** The name of the dataset. */
  protected String dataset;
  /** The default number of iterations. */
  protected static final int DEFAULT_NUM_IT = 10;
  /** The number of iterations. */
  protected static int numIt = DEFAULT_NUM_IT;
  /** The default minimum amount of training data to use (out of 10). */
  protected static final int DEFAULT_MIN_TRAINED = 1;
  /** The minimum amount of training data to use (out of 10). */
  protected static int minTrained = DEFAULT_MIN_TRAINED;
  /** The default minimum amount of training data to use (out of 10). */
  protected static final int DEFAULT_MAX_TRAINED = 9;
  /** The maximum amount of training data to use (out of 10). */
  protected static int maxTrained = DEFAULT_MAX_TRAINED;
  /** The default for whether to split temporally (TRUE) or randomly. */
  protected static final boolean DEFAULT_TEMPORAL_SPLITS = true;
  /** Whether to split temporally (TRUE) or randomly. */
  protected static boolean temporalSplits = DEFAULT_TEMPORAL_SPLITS;

  /**
   * Set up the test suite.
   *
   * @throws IOException
   * @throws BiffException
   */
  public OfflineSimulator( String review ) throws Exception {
    Collection<SystematicReview> reviews = reviews();
    this.dataset = Util.normalize( review );
    for ( SystematicReview r : reviews ) {
      if ( Util.normalize( r.getName() ).contains( this.dataset ) ) {
        this.activeReview = r;
      }
    }

    if ( this.activeReview == null )
      throw new RuntimeException( "Could not find review " + review );

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    LOG.info( "# seeds:\t " + activeReview.getSeeds().size() );
    LOG.info( "# relevant L1:\t " + activeReview.getRelevantLevel1().size() );
    LOG.info( "# relevant L2:\t " + activeReview.getRelevantLevel2().size() );
    LOG.info( "# blacklisted:\t " + activeReview.getBlacklist().size() );

    // load up the seeds
    for ( PubmedId pmid : activeReview.getSeeds() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() ); // load seeds
    }

    // load up the relevant papers
    for ( PubmedId pmid : activeReview.getRelevantLevel2() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() );
    }
  }

  @Override
  public void simulateReview() throws Exception {
    // prepare the CSV output
    FileWriter fw = new FileWriter( statsFile );
    BufferedWriter out = new BufferedWriter( fw );
    // header row
    if ( temporalSplits ) {
      out.write( "years held out, iteration, L1 AUC, L2 AUC" );
    } else {
      out.write( "% trained, iteration, L1 AUC, L2 AUC" );
    }
    out.newLine();
    out.flush();

    String popQuery = activeReview.getQueryP();
    String icQuery = activeReview.getQueryIC();
    String oQuery = activeReview.getQueryO();

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );
    LOG.info( "Initial OUTCOME query: " + oQuery );

    // run the initial query
    String query = "";
    if ( popQuery != null ) query += popQuery;
    if ( icQuery != null ) {
      if ( query.isEmpty() ) query = icQuery;
      else query = "(" + query + ") AND (" + icQuery + ")";
    }
    if ( oQuery != null ) {
      if ( query.isEmpty() ) query = oQuery;
      else query = query + " AND(" + oQuery + ")";
    }

    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher( query,
        activeReview );
    LOG.info( "Initial query: " + query );
    search( searcher );

    Set<Citation> citations = searcher.getCitations();
    if ( !temporalSplits ) {
      citations = removePostStudyArticles( citations );
    }
    Collection<Citation> downsampled = downsample(
        citations, activeReview );
    LOG.info( "Downsampled from " + citations.size() +
        " to " + downsampled.size() );

    for ( int trainPct = minTrained; trainPct <= maxTrained; trainPct++ ) {
      for ( int it = 1; it <= numIt; it++ ) {
        Collection<Citation> train = null;
        if ( temporalSplits ) {
          train = createTemporalTrainingSet( downsampled, trainPct );
        } else {
          train = createRandomTrainingSet( downsampled, trainPct * .1 );
        }
        Collection<Citation> test = createTestSet( downsampled, train );
        Map<I, C> trainingSet = createFeatureVectors( train );
        Map<I, C> testSet = createFeatureVectors( test );

        LOG.info( "Iteration " + it + ": train " + trainingSet.size()
            + ", test " + testSet.size() );

        // gather initial statistics on the results
        List<PubmedId> ranks = rank( trainingSet, testSet );
        String stats = evaluate( ranks );

        if ( temporalSplits ) {
          out.write( trainPct + "," + it + "," + stats );
        } else {
          out.write( ( trainPct * 10 ) + "," + it + "," + stats );
        }
        out.newLine();
        out.flush();
      }
    }

    out.close();
    fw.close();
  }

  /**
   * Evaluate the ranking.
   * 
   * @param ranks
   * @param fvs
   * @throws IOException
   */
  protected String evaluate( List<PubmedId> ranking ) throws IOException {
    Set<PubmedId> relevantL1 = new HashSet<PubmedId>();
    Set<PubmedId> relevantL2 = new HashSet<PubmedId>();
    for ( PubmedId id : ranking ) {
      if ( activeReview.getRelevantLevel1().contains( id ) ) {
        relevantL1.add( id );
      }
      if ( activeReview.getRelevantLevel2().contains( id ) ) {
        relevantL2.add( id );
      }
    }

    double aucL1 = computeAUC( ranking, relevantL1 );
    double aucL2 = computeAUC( ranking, relevantL2 );
    //String prL1 = computePrecisionAndRecall( ranking, relevantL1 );

    return aucL1 + "," + aucL2;
  }

  /**
   * Calculate the precision and recall.
   *
   * @param ranking
   * @param relevant
   * @return
   */
  protected <E> String computePrecisionAndRecall( List<E> ranking,
      Set<E> relevant ) {
    List<Double> precisionAtThreshold = new ArrayList<Double>();
    List<Double> recallAtThreshold = new ArrayList<Double>();
    int relevantFound = 0;
    StringBuilder sb = new StringBuilder();
    for ( int pos = 0; pos < ranking.size(); pos++ ) {
      E item = ranking.get( pos );
      if ( relevant.contains( item ) ) relevantFound++;
      double precision = (double) relevantFound / ( (double) pos + 1 );
      precisionAtThreshold.add( precision );
      double recall = (double) relevantFound / (double) relevant.size();
      recallAtThreshold.add( recall );
      sb.append( precision + "," + recall + "\n" );
    }

    return sb.toString();
  }

  /**
   * Compute the AUC for the ranking.
   * 
   * @param ranking
   * @param relevant
   * @return
   */
  protected <E> double computeAUC( List<E> ranking, Set<E> relevant ) {
    LOG.info( "Ranking size: " + ranking.size() );
    LOG.info( "Relevant size: " + relevant.size() );

    int num_eval_pairs = ( ranking.size() - relevant.size() ) * relevant.size();
    if ( num_eval_pairs < 0 ) {
      throw new IllegalArgumentException(
          "Relevant items cannot be larger than ranked items." );
    }

    if ( num_eval_pairs == 0 ) {
      return 0.5;
    }

    int numCorrectPairs = 0;
    int hitCount = 0;
    for ( E item : ranking ) {
      if ( !relevant.contains( item ) ) {
        numCorrectPairs += hitCount;
      } else {
        hitCount++;
      }
    }

    return (double) numCorrectPairs / num_eval_pairs;
  }

  /**
   * Create the temporally-split training set.
   *
   * @param fvs
   * @param numFolds
   * @param fold
   * @return
   */
  protected Collection<Citation> createTemporalTrainingSet( Collection<Citation> citations, int numYears ) {
    List<Citation> train = new ArrayList<Citation>();

    Date oldest = null;
    Date newest = null;

    for ( Citation c : citations ) {
      if ( c.getDate() != null ) {
        if ( oldest == null || c.getDate().before( oldest ) ) {
          oldest = c.getDate();
        }
        if ( newest == null || c.getDate().after( newest ) ) {
          newest = c.getDate();
        }
      }
    }

    // find the time that will split the train and test sets
    Calendar cal = new GregorianCalendar();
    cal.setTime( newest );
    cal.add( Calendar.YEAR, -1 * numYears ); // subtract numYears
    Date cutoff = cal.getTime();

    LOG.info( "Training set will be all documents before the year: " + newest );

    for ( Citation c : citations ) {
      if ( c.getDate() != null ) {
        if ( c.getDate().before( cutoff ) ) {
          train.add( c );
        }
      }
    }

    return train;
  }

  /**
   * Create the training set.
   *
   * @param fvs
   * @param numFolds
   * @param fold
   * @return
   */
  protected Collection<Citation> createRandomTrainingSet( Collection<Citation> citations, double pctData ) {
    List<Citation> train = new ArrayList<Citation>();
    Random generator = new Random();
    Citation[] values = (Citation[]) citations.toArray();

    int numInstances = (int) Math.ceil( citations.size() * pctData );

    LOG.info( "Selecting " + numInstances + " random papers out of "
        + citations.size() + "..." );

    while ( train.size() < numInstances ) {
      int i = generator.nextInt( values.length );
      Citation randPaper = values[i];
      if ( !train.contains( randPaper ) ) {
        train.add( randPaper );
      }
    }

    return train;
  }

  /**
   * Create the test set.
   * 
   * @param fvs
   * @param training
   * @return
   */
  protected Collection<Citation> createTestSet( Collection<Citation> citations,
      Collection<Citation> train ) {
    List<Citation> test = new ArrayList<Citation>();

    for ( Citation c : citations ) {
      if ( !train.contains( c ) ) {
        test.add( c );
      }
    }

    return test;
  }

  /**
   * Rank the documents.
   *
   * @param training
   * @param test
   * @return
   */
  protected abstract List<PubmedId> rank( Map<I, C> training, Map<I, C> test );

  /**
   * Remove any articles occurring after the systematic review was conducted.
   *
   * @param citations
   * @return
   */
  protected Set<Citation> removePostStudyArticles( Set<Citation> citations ) {
    Set<Citation> filtered = new HashSet<Citation>();
    for ( Citation c : citations ) {
      System.out.println( c.getDate() );
      if ( c.getDate() == null
          || !c.getDate().after( activeReview.getCreatedOn() ) ) {
        filtered.add( c );
      } else {
        if ( activeReview.getRelevantLevel1().contains( c.getPmid() ) ) {
          throw new RuntimeException( "Filtered out relevant article by date! "
              + c.getPmid() );
        }
      }
    }

    LOG.info( filtered.size() + " / " + citations.size()
        + " articles are prior to review completion." );
    return filtered;
  }

  /**
   * Turn the Citations into FeatureVectors.
   * 
   * @param citations
   * @return
   */
  protected abstract Map<I, C> createFeatureVectors( Collection<Citation> citations );
}
