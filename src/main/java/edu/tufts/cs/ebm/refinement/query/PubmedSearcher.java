package edu.tufts.cs.ebm.refinement.query;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.aliasi.spell.TfIdfDistance;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.similarity.CosineSimilarity;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceStub;

/**
 * Thread to query PubMed.
 */
public class PubmedSearcher extends PubmedService implements Runnable {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( PubmedSearcher.class );
  /** The default number of documents to retrieve per fetch. */
  protected static final int DEFAULT_FETCH_SIZE = 1000;
  /** The maximum number of documents to fetch. */
  protected int maxResults;
  /** The query complete indicator. */
  public static final String QUERY_COMPLETE = "QUERY COMPLETE";
  /** The search. */
  protected BooleanProperty searchInProg = new SimpleBooleanProperty( false );
  /** The TF-IDF instance. */
  protected static TfIdfDistance tfIdf;
  /** Citations pertaining to this query. */
  protected ListProperty<Citation> citations = new SimpleListProperty<>(
      FXCollections.observableList( new ArrayList<Citation>() ) );
  /** The current MeSH terms associated with the Review. */
  protected TreeMultiset<String> meshes = TreeMultiset.create();
  /** The active review. */
  protected SystematicReview activeReview;
  /** The current query associated with the Searcher. */
  protected String query;
  /** Optional startSignal for multithreading. */
  protected CountDownLatch startSignal = null;
  /** Optional doneSignal for multithreading. */
  protected CountDownLatch doneSignal = null;
  /** The set of papers with which to calculate similarity. */
  protected Set<Citation> compareTo = new HashSet<Citation>();

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public PubmedSearcher( String query, SystematicReview activeReview )
    throws AxisFault {
    this( query, activeReview, -1 );
  }

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public PubmedSearcher( String query, SystematicReview activeReview,
      Set<Citation> compareTo )
    throws AxisFault {
    this( query, activeReview, -1 );
    compareTo.addAll( compareTo );
  }

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public PubmedSearcher( String query, SystematicReview activeReview,
      int maxResults ) throws AxisFault {
    this.query = query;
    this.activeReview = activeReview;
    this.maxResults = maxResults;

    init();

    if ( activeReview != null ) {
      if ( activeReview.getSeedCitations().isEmpty() ) {
        PubmedLocator locator = new PubmedLocator();
        Set<Citation> seedCitations = locator.getCitations(
            activeReview.getSeeds() );
        activeReview.setSeedCitations( seedCitations );
      }
    }

    this.compareTo.addAll( activeReview.getSeedCitations() );

    TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
    tfIdf = new TfIdfDistance( tokenizerFactory );

    // train the classifier
    for ( Citation seed : compareTo ) {
      tfIdf.handle( seed.getTitle() );
      tfIdf.handle( seed.getAbstr() );
      tfIdf.handle( seed.getMeshTerms().toString() );
      System.out.println( seed.getMeshTerms().toString() );
    }
  }

  /**
   * Get all of the citations.
   *
   * @return
   */
  public ListProperty<Citation> getCitations() {
    return this.citations;
  }

  /**
   * Get all of the MeSH terms.
   *
   * @return
   */
  public SortedMultiset<String> getMeshTerms() {
    return this.meshes;
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
   * @param query
   * @param maxNrResults
   */
  public void search() throws RemoteException {
    /*
     * Search PubMed.
     */
    EUtilsServiceStub.ESearchRequest req =
        new EUtilsServiceStub.ESearchRequest();
    req.setDb( "pubmed" );
    req.setTerm( query );
    req.setUsehistory( "y" ); // important!
    EUtilsServiceStub.ESearchResult res = eUtilsService.run_eSearch( req );
    int count = new Integer( res.getCount() );
    LOG.debug( "Found " + count + " results for query '" + query + "'" );
    String webEnv = res.getWebEnv();
    String query_key = res.getQueryKey();

    if ( maxResults == -1 ) maxResults = count;

    /*
     * Fetch the results.
     */
    int fetchesPerRun = Math.min( DEFAULT_FETCH_SIZE, maxResults );
    int runs = (int) Math.ceil( count / new Double( fetchesPerRun ) );
    int start = 0;
    for ( int i = 0; i < runs; i++ ) {
      LOG.debug( "Fetching results from id " + start + " to id " + (int) ( start
          + fetchesPerRun ) );
      EFetchPubmedServiceStub.EFetchRequest req2 =
          new EFetchPubmedServiceStub.EFetchRequest();
      req2.setWebEnv( webEnv );
      req2.setQuery_key( query_key );
      req2.setRetstart( String.valueOf( start ) );
      req2.setRetmax( String.valueOf( fetchesPerRun ) );

      Collection<Citation> c = fetch( req2 );
      for ( int attempt = 1; c == null && attempt <= 3; attempt++ ) {
        LOG.warn( "Error fetching citations " + start + " to "
            + (int) ( start + fetchesPerRun ) + " on attempt " + attempt
            + "; trying again." );
        c = fetch( req2 );
      }

      if ( c != null ) {
        for ( Citation cit : c ) {
          CosineSimilarity cs = new CosineSimilarity(
              tfIdf, cit, compareTo, activeReview );
          cit.setSimilarity( cs.calculateSimilarity() );
          meshes.addAll( cit.getMeshTerms() );
        }

        setChanged();
        notifyObservers( c );
        citations.addAll( c );

        LOG.debug( "Fetched " + c.size() + " results of expected "
            + fetchesPerRun + " from id " + start + " to id "
            + (int) ( start + fetchesPerRun ) );
      } else {
        LOG.error( "Failed to fetch from id " + start + " to id "
            + (int) ( start + fetchesPerRun ) );
      }

      try {
        Thread.sleep( DEFAULT_WAIT_MS );
      } catch ( InterruptedException e ) {
        LOG.error( e );
      }
      start += fetchesPerRun;
    }

    setChanged();
    notifyObservers( QUERY_COMPLETE );
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
