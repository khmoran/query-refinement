package edu.tufts.cs.ebm.refinement.query;

import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceStub;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceStub.IdListType;

/**
 * Thread to query PubMed.
 */
public class PubmedIdentifier extends PubmedService implements Runnable {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( PubmedIdentifier.class );
  /** The default number of documents to retrieve per fetch. */
  protected static final int DEFAULT_FETCH_SIZE = 250;
  /** The maximum number of attempts per sub-query. */
  protected static final int MAX_ATTEMPTS = 3;
  /** The maximum number of documents to fetch. */
  protected int maxResults;
  /** The query complete indicator. */
  public static final String QUERY_COMPLETE = "QUERY COMPLETE";
  /** The search. */
  protected BooleanProperty searchInProg = new SimpleBooleanProperty( false );
  /** The active review. */
  protected SystematicReview activeReview;
  /** The current query associated with the Searcher. */
  protected String query;
  /** Optional startSignal for multithreading. */
  protected CountDownLatch startSignal = null;
  /** Optional doneSignal for multithreading. */
  protected CountDownLatch doneSignal = null;
  /** The ids found. */
  protected String[] ids;

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public PubmedIdentifier( String query )
    throws AxisFault {
    this( query, -1 );
  }

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public PubmedIdentifier( String query, int maxResults ) throws AxisFault {
    this.query = query;
    this.maxResults = maxResults;

    init();
  }

  /**
   * Get the search-in-progress indicator.
   * @return
   */
  public BooleanProperty getSearchInProg() {
    return this.searchInProg;
  }

  /**
   * Get the current query.
   *
   * @return
   */
  public String getQuery() {
    return this.query;
  }

  @Override
  public void run() {
    try {
      if ( startSignal != null ) {
        startSignal.await();
      }
      searchInProg.set( true );
      search();
    } catch ( RemoteException | InterruptedException e ) {
      LOG.error( "Could not execute query. Trying again.", e );
      // try again...
      try {
        search();
      } catch ( RemoteException e1 ) {
        LOG.error( "Could not execute query. Giving up.", e );
      }
    } finally {
      if ( doneSignal != null ) {
        doneSignal.countDown();
      }
    }
  }

  /**
   * Default search.
   * @throws RemoteException
   */
  public void search() throws RemoteException {
    search( 0, 1 );
  }

  /**
   * @param query
   * @param maxNrResults
   */
  public void search( int start, int attempt ) throws RemoteException {
    /*
     * Search PubMed.
     */
    EUtilsServiceStub.ESearchRequest req =
        new EUtilsServiceStub.ESearchRequest();
    req.setDb( "pubmed" );
    req.setTerm( query );
    req.setRetStart( String.valueOf( start ) );
    req.setUsehistory( "y" ); // important!
    req.setRetMax( String.valueOf( Integer.MAX_VALUE ) );
    EUtilsServiceStub.ESearchResult res = eUtilsService.run_eSearch( req );
    int total = new Integer( res.getCount() ); // update the count
    LOG.debug( "Found " + total + " results for query '" + query + "'" );

    IdListType ilt = res.getIdList();

    ids = ilt.getId();

    setChanged();
    notifyObservers( QUERY_COMPLETE );
  }

  /**
   * Get the ids found.
   * @return
   */
  public String[] getIds() {
    return this.ids;
  }

  /**
   * Set the done signal.
   *
   * @param doneSignal
   */
  public void setDoneSignal( CountDownLatch doneSignal ) {
    this.doneSignal = doneSignal;
  }

  /**
   * Set the start signal.
   *
   * @param startSignal
   */
  public void setStartSignal( CountDownLatch startSignal ) {
    this.startSignal = startSignal;
  }
}
