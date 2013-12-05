package edu.tufts.cs.ebm.review.systematic;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import edu.tufts.cs.ebm.mesh.RankedMesh;
import edu.tufts.cs.ebm.mesh.TestLoadMeshRanking;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.PicoElement;
import edu.tufts.cs.ebm.refinement.query.PubmedIdentifier;
import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.util.Util;

/**
 * Test the MeshWalker class.
 */
public class MeshAdder extends AbstractTest implements Observer {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( MeshAdder.class );
  /** The file containing the ranking of MeSH terms by info gain. */
  protected static final String MESH_RANKING =
      "src/test/resources/meshRanking.out";
  /** The info gain map. */
  protected TreeSet<RankedMesh> rankedMeshes;
  /** The true positive counts. */
  protected Map<PubmedIdentifier, Integer> truePosCt = new HashMap<>();
  /** The total counts. */
  protected Map<PubmedIdentifier, Integer> totalCt = new HashMap<>();
  /** The total counts. */
  protected Map<PubmedIdentifier, Set<String>> meshes = new HashMap<>();

  /**
   * Get those terms with the highest info gain.
   * @return
   */
  protected String getBestTerm( Set<String> meshes, Set<String> used,
      PicoElement elem ) {
    // only include those MeSH terms returned by the query
    TreeSet<RankedMesh> present = new TreeSet<RankedMesh>();
    for ( RankedMesh ranked : rankedMeshes ) {
      String term = ranked.getTerm();
      // term must be positive, used in a citation returned by the query,
      // not yet used, and for the appropriate PICO element
      if ( ranked.isPositive() // && meshes.contains( term )
          && !used.contains( term ) && ranked.getPico() == elem ) {
        present.add( ranked ); // TODO work in "NOT" for negatives?
      }
    }

    // get the set of terms with the highest info gain
    if ( !present.isEmpty() ) {
      RankedMesh best = present.last();
      LOG.info( "The highest (positive) term info gain is: " + best );
      return best.getTerm();
    } else {
      LOG.info( "No term meets the criteria." );
      return null;
    }
  }

  /**
   * Get the MeSH terms from the seed papers.
   * @return
   */
  protected Multiset<String> getSeedMeshes() {
    TreeMultiset<String> seedMeshes = TreeMultiset.create();
    for ( Citation c : clopidogrelReview.getSeedCitations() ) {
      if ( c.getMeshTerms() != null ) {
        seedMeshes.addAll( c.getMeshTerms() );
      }
    }

    return seedMeshes;
  }

  /**
   * Refine the given search using the provided term.
   * @param searcher
   * @param term
   * @return
   * @throws AxisFault
   */
  protected Entry<PubmedIdentifier, InfoMeasure> refineSearch(
      PubmedIdentifier searcher, InfoMeasure prevInfo, String term )
    throws AxisFault {
    String orQuery = searcher.getQuery() + " OR " + term;
    String andQuery = searcher.getQuery() + " AND " + term;
    PubmedIdentifier orSearcher = new PubmedIdentifier( orQuery );
    orSearcher.addObserver( this );
    PubmedIdentifier andSearcher = new PubmedIdentifier( andQuery );
    andSearcher.addObserver( this );

    Map<PubmedIdentifier, InfoMeasure> results = searchConcurrent(
        orSearcher, andSearcher );

    InfoMeasure orResult = results.get( orSearcher );
    InfoMeasure andResult = results.get( andSearcher );

    LOG.info( "OR query stats: " + orResult );
    LOG.info( "AND query stats: " + andResult );

    // want the most restrictive query that still produces the best recall
    // so if their recalls are the same, choose AND; if the OR recall is
    // better than the original, choose OR; otherwise the originalc
    if ( andResult.getRecall() >= orResult.getRecall() ) {
      return new SimpleEntry<PubmedIdentifier, InfoMeasure>(
          andSearcher, andResult );
    } else if ( orResult.getRecall() > prevInfo.getRecall() ) {
      return new SimpleEntry<PubmedIdentifier, InfoMeasure>(
          orSearcher, orResult );
    } else {
      return null;
    }
  }

  /**
   * Execute the search.
   * @param query
   * @return
   */
  protected InfoMeasure search( PubmedIdentifier searcher ) {
    try {
      Thread t = new Thread( searcher );
      t.start();
      t.join();
      InfoMeasure im = calculateInfoMeasureL1( clopidogrelReview,
          truePosCt.get( searcher ), totalCt.get( searcher ) );

      // clean up the map
      truePosCt.remove( searcher );
      totalCt.remove( searcher );

      return im;
    } catch ( InterruptedException e ) {
      LOG.error( "Could not complete query on PubMed. Exiting.", e );
      System.exit( 1 );
    }

    return null;
  }

  /**
   * Execute the searches concurrently.
   * @return
   */
  protected Map<PubmedIdentifier, InfoMeasure> searchConcurrent(
      Collection<PubmedIdentifier> searchers ) {
    CountDownLatch startSignal = new CountDownLatch( 1 );
    CountDownLatch doneSignal = new CountDownLatch( searchers.size() );

    Map<PubmedIdentifier, InfoMeasure> results = new HashMap<>();
    try {
      for ( PubmedIdentifier searcher : searchers ) {
        searcher.setStartSignal( startSignal );
        searcher.setDoneSignal( doneSignal );
        Thread t = new Thread( searcher );
        t.start();
      }

      startSignal.countDown(); // let all threads proceed
      doneSignal.await();  // wait til they're all done

      Thread.sleep( 10000 );

      for ( PubmedIdentifier s : searchers ) {
        InfoMeasure im = calculateInfoMeasureL1( clopidogrelReview,
            truePosCt.get( s ), totalCt.get( s ) );
        results.put( s, im );

        // clean up the map
        truePosCt.remove( s );
        totalCt.remove( s );
      }

    } catch ( InterruptedException e ) {
      LOG.error( "Could not complete query on PubMed. Exiting.", e );
      System.exit( 1 );
    }

    return results;
  }

  /**
   * Execute the searches concurrently.
   * @param queries
   * @return
   */
  protected Map<PubmedIdentifier, InfoMeasure> searchConcurrent(
      PubmedIdentifier... queries ) {
    return searchConcurrent( Arrays.asList( queries ) );
  }

  /**
   * Set up the test suite.
   * @throws IOException
   */
  @Override
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    TestLoadMeshRanking meshRanker = new TestLoadMeshRanking();
    try {
      Reader r = new FileReader( MESH_RANKING );
      rankedMeshes = meshRanker.loadInfoGains( r );
    } catch ( IOException | ParseException e ) {
      LOG.error( "Could not load MeshRanker. Exiting.", e );
      System.exit( 1 );
    }

    // load up the relevant papers
    for ( PubmedId pmid : clopidogrelReview.getRelevantLevel1() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() );
    }
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws RemoteException
   * @throws InterruptedException
   */
  @Test
  public void simulateClopidogrelReview()
    throws RemoteException, InterruptedException {

    Set<String> usedTerms = new HashSet<>();

    // run the initial query
    PubmedIdentifier popSearcher = new PubmedIdentifier(
        clopidogrelReview.getQueryP() );
    popSearcher.addObserver( this );
    truePosCt.put( popSearcher, 0 );
    totalCt.put( popSearcher, 0 );
    PubmedIdentifier icSearcher = new PubmedIdentifier(
        clopidogrelReview.getQueryIC() );
    icSearcher.addObserver( this );
    truePosCt.put( icSearcher, 0 );
    totalCt.put( icSearcher, 0 );

    LOG.info( "Initial POPULATION query: " + clopidogrelReview.getQueryP() );
    LOG.info( "Initial INTERVENTION query: " + clopidogrelReview.getQueryIC() );

    Map<PubmedIdentifier, InfoMeasure> results = searchConcurrent(
        popSearcher, icSearcher );
    InfoMeasure popInfo = results.get( popSearcher );
    InfoMeasure icInfo = results.get( icSearcher );

    LOG.info( "Initial POPULATION metrics: " + popInfo );
    LOG.info( "Initial INTREVENTION metrics: " + icInfo );

    // MeSH terms from the seed papers
    // Multiset<String> seedMeshes = getSeedMeshes(); // TODO work this in

    int i = 0;
    while ( ( popSearcher != null || icSearcher != null ) ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );
      // Refine the Population sub-query, if possible
      if ( popSearcher != null ) {
        // Get the MeSH terms found during the population search
        Set<String> popMeshes = meshes.get( popSearcher );
        // Get the MeSH term with the highest info gain
        String popTerm = getBestTerm( popMeshes, usedTerms,
            PicoElement.POPULATION );
        if ( popTerm == null ) {
          popSearcher = null;  // ran out of terms... done
          continue;
        }
        usedTerms.add( popTerm ); // don't repeat terms
        meshes.remove( popSearcher ); // clean up for next time
        // Refine the search with the given MeSH term
        Entry<PubmedIdentifier, InfoMeasure> result = refineSearch(
            popSearcher, popInfo, popTerm );
        if ( result != null ) { // MeSH term has been added to the query
          popSearcher = result.getKey();
          popInfo = result.getValue();
          LOG.info( "-- Updated POPULATION query: " + popSearcher.getQuery() );
          LOG.info( "-- Updated POPULATION metrics: " + popInfo );
        } else { // Neither "OR"ing or "AND"ing the term improved results
          LOG.info( "-- Not updating POPULATION query." );
        }
      }
      // Refine the intervention sub-query, if possible
      if ( icSearcher != null ) {
        // Get the MeSH terms found during the I/C search
        Set<String> icMeshes = meshes.get( icSearcher );
        // Get the MeSH term with the highest info gain
        String icTerm = getBestTerm( icMeshes, usedTerms,
            PicoElement.INTERVENTION );
        if ( icTerm == null ) { // ran out of terms... done
          icSearcher = null;
          continue;
        }
        usedTerms.add( icTerm ); // don't repeat terms
        meshes.remove( icSearcher ); // clean up for next time
        // Refine the search with the given MeSH term
        Entry<PubmedIdentifier, InfoMeasure> result = refineSearch(
            icSearcher, icInfo, icTerm );
        if ( result != null ) { // MeSH term has been added to the query
          icSearcher = result.getKey();
          icInfo = result.getValue();
          LOG.info( "-- Updated INTERVENTION query: " + icSearcher.getQuery() );
          LOG.info( "-- Updated INTERVENTION metrics: " + icInfo );
        } else { // Neither "OR"ing or "AND"ing the term improved results
          LOG.info( "-- Not updating INTERVENTION query." );
        }
      }
    }
  }

  @SuppressWarnings( "unchecked" )
  @Override
  public void update( Observable o, Object arg ) {
    if ( o instanceof PubmedIdentifier && arg.getClass().isArray() ) {
      PubmedIdentifier query = (PubmedIdentifier) o;
      String[] pmids = (String[]) arg;

      if ( !truePosCt.containsKey( query ) ) {
        truePosCt.put( query, 0 );
      }
      if ( !totalCt.containsKey( query ) ) {
        totalCt.put( query, 0 );
      }
      if ( !meshes.containsKey( query ) ) {
        meshes.put( query, new HashSet<String>() );
      }

      for ( String id : pmids ) {
        long l = Long.valueOf( id );
        PubmedId pmid = Util.createOrUpdatePmid( l );

        if ( pmid != null ) {
          if ( clopidogrelReview.getRelevantLevel1().contains( pmid ) ) {
            int old = truePosCt.get( query );
            truePosCt.put( query, ++old );
          }

          int old = totalCt.get( query );
          totalCt.put( query, ++old );
        }
      }

      Runtime rt = Runtime.getRuntime();
      LOG.debug( "Free Memory: " + rt.freeMemory() / MB );
      LOG.info( "\tTrue positive counts: " +
          entryToString( query, truePosCt.get( query ) ) +
          "\n\tTotal counts: " + entryToString( query, totalCt.get( query ) ) );
      // we are throwing out mesh terms that we can't use for info gain
    } else if ( o instanceof PubmedIdentifier && arg instanceof Set ) {
      PubmedIdentifier query = (PubmedIdentifier) o;
      Set<String> newMeshes = (Set<String>) arg;
      Set<String> old = meshes.get( query );

      if ( old == null ) {
        old = new HashSet<String>();
        meshes.put( query, old );
      }

      for ( RankedMesh rm : rankedMeshes ) {
        if ( newMeshes.contains( rm.getTerm() ) ) {
          old.add( rm.getTerm() );
        }
      }
    }
  }

  /**
   * Stringify the map.
   * @param map
   * @return
   */
  public String entryToString( PubmedIdentifier key, Object value ) {
    StringBuilder sb = new StringBuilder();
    String valueStr = value.toString();
    if ( value instanceof Set ) {
      Set<?> s = (Set<?>) value;
      valueStr = String.valueOf( s.size() );
    }

    sb.append( "\"" + key.getQuery() + "\": \t" + valueStr );
    return sb.toString();
  }

  /**
   * Calculate the info measure.
   * @param pmids
   * @return
   */
  protected InfoMeasure calculateInfoMeasure( String[] pmids ) {
    int truePos = 0;

    for ( String id : pmids ) {
      long l = Long.valueOf( id );
      PubmedId pmid = Util.createOrUpdatePmid( l );

      if ( pmid != null ) {
        if ( clopidogrelReview.getRelevantLevel1().contains( pmid ) ) {
          truePos++;
        }
      }
    }

    return new InfoMeasure(
        truePos, pmids.length, clopidogrelReview.getRelevantLevel1().size() );

  }
}
