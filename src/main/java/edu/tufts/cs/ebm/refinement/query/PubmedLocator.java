package edu.tufts.cs.ebm.refinement.query;

import java.util.Collection;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.util.Util;

/**
 * Thread to query PubMed.
 */
public class PubmedLocator extends PubmedService implements Observer {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( PubmedLocator.class );
  /** The number of threads to fork off at a time. */
  protected static final int NUM_FORKS = 15;
  /** The number of minutes after which to time out the request. */
  protected static final int TIMEOUT_MINS = 60;
  /** The citations. */
  protected Set<Citation> citations = new HashSet<>();

  /**
   * Default constructor.
   * @throws AxisFault
   */
  public PubmedLocator() throws AxisFault {
    init();
  }

  /**
   * Get the citation from the PubmedId.
   *
   * @param pmid
   */
  public Citation getCitation( PubmedId pmid ) {
    Citation c = fetch( pmid );
    for ( int attempt = 1; c == null && attempt <= DEFAULT_TRIES; attempt++ ) {
      LOG.warn( "Error fetching citation " + pmid + " on attempt " + attempt
          + "; trying again." );

      try { // sleep for 15 minutes to prevent rate limiting
        Thread.sleep( DEFAULT_WAIT_MS );
        init(); // reinitialize the service before trying again
      } catch ( InterruptedException | AxisFault e ) {
        // TODO ???
      }

      c = fetch( pmid );
    }

    return c;
  }

  /**
   * Get the citations for the PubmedIds.
   *
   */
  public Set<Citation> getCitations( Collection<PubmedId> pmids ) {
    LOG.debug( "Searching for " + pmids.size() + " citations." );
    Set<Citation> cits = new HashSet<>();
    for ( PubmedId id : pmids ) {
      Citation c = getCitation( id );
      cits.add( c );
    }

    return cits;
  }

  /**
   * Get the citations for the PubmedIds.
   * @throws AxisFault
   *
   */
  public Set<Citation> getCitations( String[] pmids ) throws AxisFault {
    LOG.debug( "Looking up " + pmids.length + " citations." );

    ExecutorService executorService = Executors.newFixedThreadPool( NUM_FORKS );
    for ( int i = 0; i < pmids.length; i++ ) {
      Long l = Long.valueOf( pmids[i] );
      PubmedId id = Util.createOrUpdatePmid( l );
      ParallelPubmedLocator locator = new ParallelPubmedLocator( id );
      locator.addObserver( this );
      executorService.submit( locator );
    }

    executorService.shutdown();

    try {
      executorService.awaitTermination( TIMEOUT_MINS, TimeUnit.MINUTES );
      LOG.info( "Found all " + pmids.length + " citations." );
    } catch ( InterruptedException e ) {
      LOG.error( e );
    }

    return citations;
  }

  @Override
  public void update( Observable o, Object arg ) {
    if ( arg instanceof Citation && arg != null ) {
      this.citations.add( (Citation) arg );
    }

    if ( citations.size() % 100 == 0 ) {
      LOG.debug( "\tFound " + citations.size() + " citations so far." );
    }
  }
}
