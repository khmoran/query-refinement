package edu.tufts.cs.ebm.refinement.query;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.aliasi.spell.TfIdfDistance;
import com.aliasi.tokenizer.EnglishStopTokenizerFactory;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;
import edu.tufts.cs.similarity.CosineSimilarity;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleType;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceStub;

/**
 * Thread to query PubMed.
 */
public class ParallelPubmedSearcher extends PubmedService
  implements Runnable, Observer {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog(
      ParallelPubmedSearcher.class );
  /** The default number of documents to retrieve per fetch. */
  protected static final int DEFAULT_FETCH_SIZE = 100;
  /** The number of threads to fork off at a time. */
  protected static final int NUM_FORKS = 8;
  /** The number of minutes after which to time out the request. */
  protected static final int TIMEOUT_MINS = 120;
  /** The maximum number of documents to fetch. */
  protected int maxResults;
  /** The query complete indicator. */
  public static final String QUERY_COMPLETE = "QUERY COMPLETE";
  /** The search. */
  protected BooleanProperty searchInProg = new SimpleBooleanProperty( false );
  /** The title TF-IDF instance. */
  protected static TfIdfDistance tfIdf;
  /** Citations pertaining to this query. */
  protected Set<Citation> citations = new CopyOnWriteArraySet<>();
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
  public ParallelPubmedSearcher( String query, SystematicReview activeReview,
      Set<Citation> compareTo )
    throws AxisFault {
    this( query, activeReview, -1 );
    this.compareTo.addAll( compareTo );
  }

  /**
   * Default constructor.
   *
   * @throws AxisFault
   */
  public ParallelPubmedSearcher( String query, SystematicReview activeReview,
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

    TokenizerFactory tokenizerFactory = new EnglishStopTokenizerFactory(
        IndoEuropeanTokenizerFactory.INSTANCE );
    tfIdf = new TfIdfDistance( tokenizerFactory );

    // train the classifier
    for ( Citation seed : compareTo ) {
      tfIdf.handle( seed.getTitle() );
      tfIdf.handle( seed.getAbstr() );
      //tfIdf.handle( seed.getMeshTerms().toString() );
    }

  }

  /**
   * Get all of the citations.
   *
   * @return
   */
  public Set<Citation> getCitations() {
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
    req.setRetMax( String.valueOf( Integer.MAX_VALUE ) );
    EUtilsServiceStub.ESearchResult res = eUtilsService.run_eSearch( req );
    int total = new Integer( res.getCount() ); // update the count
    LOG.debug( "Found " + total + " results for query '" + query + "'" );

    LOG.debug( "# citations to look up: " + res.getIdList().getId().length );

    String[] ids = res.getIdList().getId();
    int iterations = (int) Math.ceil( (double) ids.length/
        (double) DEFAULT_FETCH_SIZE );
    for ( int i = 0; i < iterations; i++ ) {
      int min = i*DEFAULT_FETCH_SIZE;
      int max = Math.min( min+DEFAULT_FETCH_SIZE, ids.length );
      LOG.debug( "Searching from " +  min + " to " + max );
      String[] part = Arrays.copyOfRange( ids, min, max );
      getCitations( part );
      LOG.debug( "\tFound " +  citations.size() + " so far" );
    }

    setChanged();
    notifyObservers( citations );

    LOG.debug( "Fetched " + citations.size() + " results of expected "
        + total );

    setChanged();
    notifyObservers( QUERY_COMPLETE );
  }

  /**
   * Get the citations for the PubmedIds.
   * @throws AxisFault
   *
   */
  public void getCitations( String[] pmids ) throws AxisFault {
    //init();

    ExecutorService executorService = Executors.newFixedThreadPool( NUM_FORKS );
    for ( int i = 0; i < pmids.length; i++ ) {
      Long l = Long.valueOf( pmids[i] );
      PubmedId id = Util.createOrUpdatePmid( l );

      if ( activeReview != null &&
          activeReview.getBlacklist().contains( id ) ) {
        LOG.warn( "\tOn blacklist: " + id );
        continue;
      }

      Citation c = Util.getCitation( id );
      if ( c != null ) {
        this.update( this, c );
      } else {
        ParallelPubmedLocator locator = new ParallelPubmedLocator(
            activeReview, id );
        locator.addObserver( this );
        executorService.submit( locator );
      }
    }

    executorService.shutdown();

    try {
      executorService.awaitTermination( TIMEOUT_MINS, TimeUnit.MINUTES );
    } catch ( InterruptedException e ) {
      LOG.error( e );
    }
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

  @Override
  protected Citation articleToCitation( PubmedArticleType articleType ) {
    Citation c = super.articleToCitation( articleType );

    if ( c != null ) {
      CosineSimilarity cs = new CosineSimilarity( defaultCache,
          tfIdf, c, compareTo, activeReview );
      c.setSimilarity( cs.calculateSimilarity() );
      meshes.addAll( c.getMeshTerms() );
    }

    return c;
  }


  @Override
  public void update( Observable o, Object arg ) {
    if ( arg instanceof Citation ) {
      Citation c = (Citation) arg;
      this.citations.add( c );

      CosineSimilarity cs = new CosineSimilarity( defaultCache,
          tfIdf, c, compareTo, activeReview );
      c.setSimilarity( cs.calculateSimilarity( ) );
    }
  }

  /**
   * Update the similarity values.
   */
  public void updateSimilarities( Set<Citation> relevant ) {
    compareTo.addAll( relevant );
    LOG.debug( "Comparing " + citations.size() + " citations to " +
        compareTo.size() + " papers for similarity." );

    // parallelized
    ExecutorService executorService = Executors.newFixedThreadPool( NUM_FORKS );
    for ( Citation c : citations ) {
      CosineSimilarity cs = new CosineSimilarity( defaultCache,
          tfIdf, c, compareTo, activeReview );
      executorService.submit( cs );
    }

    executorService.shutdown();

    try {
      executorService.awaitTermination( TIMEOUT_MINS, TimeUnit.MINUTES );
    } catch ( InterruptedException e ) {
      LOG.error( e );
    }
  }
}
