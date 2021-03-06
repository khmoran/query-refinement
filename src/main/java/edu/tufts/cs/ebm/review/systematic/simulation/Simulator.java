package edu.tufts.cs.ebm.review.systematic.simulation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.naming.NamingException;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.springframework.context.annotation.Bean;

import edu.tufts.cs.ebm.refinement.Launcher;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ml.FeatureVector;

/**
 * Simulate a systematic review.
 */
public abstract class Simulator {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( Simulator.class );
  /** Megabytes. */
  protected static final int MB = 1024 * 1024;
  /** The number of minutes after which to time out the request. */
  protected static final int TIMEOUT_MINS = 60;
  /** The number of threads to fork off at a time. */
  protected static final int NUM_FORKS = 8;
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 5;
  /** The positive class label. */
  protected static final int POS = 1;
  /** The negative class label. */
  protected static final int NEG = -1;
  /** The cache name. */
  protected static final String DEFAULT_CACHE_NAME = "default";
  /** The data cache. */
  protected static JCS defaultCache;
  /** The file containing the recall statistics. */
  protected String statsFile = "stats.csv";
  /** The file containing the rankings of the papers. */
  protected String paperRankFile = "ranks.csv";
  /** The file containing the probabilities of the papers. */
  protected String paperProbFile = "probs.csv";
  /** The iteration. */
  protected long iteration = 0;
  /** The z value for probability calculations. */
  protected double z = -1;
  /** The default maximum number of negative citations. */
  protected static final int DEFAULT_MAX_NEGATIVE = 10000;
  /** The maximum number of negative citations. */
  protected static int maxNegative = DEFAULT_MAX_NEGATIVE;
  /** The document ID prefix for a pseudo document term. */
  protected static final String PSEUDO_PREFIX = "pseudo_";
  /** The pseduo document positive class label. */
  protected static final int PSEUDO_POS = POS;
  /** The pseduo document negative class label. */
  protected static final int PSEUDO_NEG = NEG;
  /** Labeled terms. */
  protected Map<FeatureVector<Integer>, Integer> labeledTerms =
      new HashMap<FeatureVector<Integer>, Integer>();
  /** The default number of times to replicate a pseudo document. */
  protected static final int DEFAULT_NUM_REPLICATIONS = 1;
  /** The number of times to replicate a pseudo document. */
  protected static final int numReplications = DEFAULT_NUM_REPLICATIONS;

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected static InfoMeasure calculateInfoMeasureL1( SystematicReview r,
      int truePositives ) {
    InfoMeasure im = new InfoMeasure( truePositives, r.getRelevantLevel1()
        .size() );

    return im;
  }

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected static InfoMeasure calculateInfoMeasureL1( SystematicReview r,
      int truePositives, int total ) {
    InfoMeasure im = new InfoMeasure( truePositives, total, r
        .getRelevantLevel1().size() );

    return im;
  }

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected static InfoMeasure calculateInfoMeasureL2( SystematicReview r,
      int truePositives ) {
    InfoMeasure im = new InfoMeasure( truePositives, r.getRelevantLevel2()
        .size() );

    return im;
  }

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected static InfoMeasure calculateInfoMeasureL2( SystematicReview r,
      int truePositives, int total ) {
    InfoMeasure im = new InfoMeasure( truePositives, total, r
        .getRelevantLevel2().size() );

    return im;
  }

  /**
   * Calculate z.
   * 
   * @param n
   * @return
   */
  protected static double calcZ( int n ) {
    double z = 0;
    for ( int i = 1; i <= n; i++ ) {
      z += 1 / (double) ( i );
    }

    return z;
  }

  /**
   * Load up the systematic reviews from the database.
   * 
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @SuppressWarnings("unchecked")
  @Bean
  protected static Set<Citation> citations() throws NamingException,
      ClassNotFoundException, SQLException {
    Query q = MainController.EM
        .createQuery( "select m from SystematicReview m" );
    Set<Citation> citations = new HashSet<Citation>( q.getResultList() );

    return citations;
  }

  /**
   * Load up the systematic reviews from the database.
   * 
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @SuppressWarnings("unchecked")
  @Bean
  protected static ObservableList<SystematicReview> reviews()
      throws NamingException, ClassNotFoundException, SQLException {

    Query q = MainController.EM
        .createQuery( "select m from SystematicReview m" );
    List<SystematicReview> list = q.getResultList();

    ObservableList<SystematicReview> reviews = FXCollections
        .observableArrayList( list );

    Collections.sort( reviews );

    return reviews;
  }

  /**
   * Perform the search and rank the results.
   * 
   * @param searcher
   * @return
   * @throws InterruptedException
   */
  protected static void search( ParallelPubmedSearcher searcher ) {
    try {
      LOG.info( "Performing search..." );
      Thread t = new Thread( searcher );
      t.start();
      t.join();
    } catch ( InterruptedException e ) {
      LOG.error( "Could not complete query on PubMed. Exiting.", e );
      System.exit( 1 );
    }
  }

  /**
   * Set the proxy information.
   */
  protected static void setProxy() {
    if ( System.getenv( Launcher.JAVA_OPTS_ENV ) != null ) {
      String[] opts = System.getenv( Launcher.JAVA_OPTS_ENV ).split( " " );

      for ( String opt : opts ) {
        String[] pair = opt.split( "=" );
        if ( pair.length > 1 ) {
          String key = pair[0].replace( "-D", "" );
          String value = pair[1];
          if ( key.contains( "http.proxy" ) ) { // set only proxy properties
            System.setProperty( key, value );
          }
        }
      }
    }

    LOG.info( "Proxy set: " + System.getProperty( "http.proxySet" ) );
    LOG.info( "Proxy host: " + System.getProperty( "http.proxyHost" ) );
    LOG.info( "Proxy port: " + System.getProperty( "http.proxyPort" ) );
    LOG.info( "Non-proxy hosts: " + System.getProperty( "http.nonProxyHosts" ) );
  }

  /**
   * Simulate the review.
   *
   * @throws Exception
   */
  public abstract void simulateReview() throws Exception;


  /**
   * Downsample the negative instances.
   * @param citations
   * @param r
   * @return
   */
  protected Collection<Citation> downsample( Collection<Citation> citations,
      SystematicReview r ) {
    List<Citation> downsampled = new ArrayList<Citation>();

    ArrayList<Citation> citList = new ArrayList<>(
        citations );

    // so we don't get stuck in an infinite loop
    if ( maxNegative > citations.size() ) maxNegative = citations.size() -
        r.getRelevantLevel1().size() - r.getRelevantLevel2().size();

    int totalNegative = citations.size() - r.getRelevantLevel1().size()
        - r.getRelevantLevel2().size();
    double proportion = (double) totalNegative / (double) maxNegative;
    int n = (int) Math.ceil( proportion );

    // include every nth negative record for a deterministic downsampling
    while ( downsampled.size() < maxNegative ) {
      for ( int i = 0; i < citList.size(); i += n ) {
        if ( downsampled.size() >= maxNegative ) break;
        Citation c = citList.get( i );
        if ( !( r.getRelevantLevel1().contains( c.getPmid() )
            || r.getRelevantLevel2().contains( c.getPmid() ) ) ) {
          downsampled.add( c );
        }
      }
      citList.removeAll( downsampled );
    }

    // add in all positives
    for ( Citation c : citations ) {
      if ( r.getRelevantLevel1().contains( c.getPmid() )
        || r.getRelevantLevel2().contains( c.getPmid() ) ) {
        downsampled.add( c );
      }
    }

    System.out.println( downsampled.size() );
    return downsampled;
  }
}
