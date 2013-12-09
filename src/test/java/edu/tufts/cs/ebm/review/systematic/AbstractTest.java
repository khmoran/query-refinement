package edu.tufts.cs.ebm.review.systematic;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.naming.NamingException;
import javax.persistence.Query;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.Bean;
import org.testng.annotations.BeforeSuite;

import edu.tufts.cs.ebm.refinement.Launcher;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.controller.MainController;

public class AbstractTest extends TestCase {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( AbstractTest.class );
  /** Megabytes. */
  protected static final int MB = 1024 * 1024;
  /** The active review. */
  protected SystematicReview clopidogrelReview;
  /** The active review. */
  protected SystematicReview protonBeamReview;

  /**
   * Set up the test suite.
   * 
   * @throws Exception
   */
  @Override
  @BeforeSuite
  public void setUp() throws Exception {
    setProxy();

    if ( reviews().isEmpty() ) {
      LOG.error( "No reviews in database." );
      return;
    }

    try {
      clopidogrelReview = reviews().get( 0 );
    } catch ( ClassNotFoundException | NamingException | SQLException e ) {
      LOG.error( "Could not load Clopidogrel review. Exiting.", e );
      System.exit( 1 );
    }
    assert clopidogrelReview.getName().equalsIgnoreCase( "Clopidogrel" );

    if ( reviews().size() == 1 ) {
      return;
    }

    try {
      protonBeamReview = reviews().get( 1 );
    } catch ( ClassNotFoundException | NamingException | SQLException e ) {
      LOG.error( "Could not load Clopidogrel review. Exiting.", e );
      System.exit( 1 );
    }
    assert protonBeamReview.getName().equalsIgnoreCase( "Proton Beam Therapy" );

    // load up the seeds
    for ( PubmedId pmid : clopidogrelReview.getSeeds() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() ); // load the
                                                                 // seeds
    }

    // load up the seeds
    for ( PubmedId pmid : protonBeamReview.getSeeds() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() ); // load the
                                                                 // seeds
    }
  }

  /**
   * Set the proxy information.
   */
  protected void setProxy() {
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
   * Perform the search and rank the results.
   * 
   * @param searcher
   * @return
   * @throws InterruptedException
   */
  protected void search( ParallelPubmedSearcher searcher ) {
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
   * Load up the systematic reviews from the database.
   * 
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @Bean
  protected ObservableList<SystematicReview> reviews() throws NamingException,
      ClassNotFoundException, SQLException {
    String sql = "select t from SystematicReview t";
    Query q = MainController.EM.createQuery( sql );
    List<SystematicReview> list = q.getResultList();

    System.out.println( "# systematic reviews: " + list.size() );

    ObservableList<SystematicReview> reviews = FXCollections
        .observableArrayList( list );

    if ( !reviews.isEmpty() ) {
      Collections.sort( reviews );
    }

    return reviews;
  }

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected InfoMeasure calculateInfoMeasureL1( SystematicReview r,
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
  protected InfoMeasure calculateInfoMeasureL2( SystematicReview r,
      int truePositives, int total ) {
    InfoMeasure im = new InfoMeasure( truePositives, total, r
        .getRelevantLevel2().size() );

    return im;
  }

  /**
   * Calculate the info measures for the searcher.
   * 
   * @param s
   * @return
   */
  protected InfoMeasure calculateInfoMeasureL1( SystematicReview r,
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
  protected InfoMeasure calculateInfoMeasureL2( SystematicReview r,
      int truePositives ) {
    InfoMeasure im = new InfoMeasure( truePositives, r.getRelevantLevel2()
        .size() );

    return im;
  }

  /**
   * Load up the systematic reviews from the database.
   * 
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @Bean
  protected Set<Citation> citations() throws NamingException,
      ClassNotFoundException, SQLException {
    String sql = "select t from Citation t";
    Query q = MainController.EM.createQuery( sql );
    Set<Citation> citations = new HashSet<Citation>( q.getResultList() );

    return citations;
  }
}
