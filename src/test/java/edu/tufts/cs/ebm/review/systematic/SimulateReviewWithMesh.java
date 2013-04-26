package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.mesh.RankedMesh;
import edu.tufts.cs.ebm.mesh.TestLoadMeshRanking;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewWithMesh extends SimulateReviewClopidogrel {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewWithMesh.class );
  /** The number of MeSH terms to propose to the expert per iteration. */
  protected static final int MESH_PROPOSALS_PER_ITERATION = 10;
  /** The threshold for information gain being "useful". */
  protected static final double INFO_GAIN_THRESHOLD = 0.4;
  /** The file containing the ranking of MeSH terms by info gain. */
  protected static final String MESH_RANKING =
      "src/test/resources/meshRanking.out";
  /** The info gain map. */
  protected TreeSet<RankedMesh> rankedMeshes;

  /**
   * Get the MeSH terms to propose.
   * @param query
   * @return
   */
  protected Set<String> getMeshProposals( ParallelPubmedSearcher searcher,
      Set<String> expertRelevantTerms, Set<String> expertIrrelevantTerms,
      Set<Citation> expertRelevantPapers ) {
    Set<String> results = new HashSet<>();

    LOG.info( "Getting term proposal set..." );
  outer:
    for ( Citation c : expertRelevantPapers ) {
      for ( String term : c.getMeshTerms() ) {
        if ( results.size() == MESH_PROPOSALS_PER_ITERATION ) {
          break outer;
        } else if ( !expertRelevantTerms.contains( term ) // not yet propos
                 && !expertIrrelevantTerms.contains( term ) ) {
          results.add( term );
        }
      }
    }

    LOG.info(  "Term proposals: " + results );
    return results;
  }

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();
    TestLoadMeshRanking meshRanker = new TestLoadMeshRanking();
    try {
      Reader r = new FileReader( MESH_RANKING );
      rankedMeshes = meshRanker.loadInfoGains( r );
    } catch ( IOException | ParseException e ) {
      LOG.error( "Could not load MeshRanker. Exiting.", e );
      System.exit( 1 );
    }
  }

  /**
   * Propose the terms to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<RankedMesh> proposeTerms( Set<String> proposals,
      Set<String> relevant, Set<String> irrelevant ) {
    LOG.info( "Proposing MeSH terms... (" + proposals.size() + ")" );
    Set<RankedMesh> newRelevant = new HashSet<>();
    for ( RankedMesh rm : rankedMeshes ) {
      if ( proposals.contains( rm.getTerm() ) ) {
        if ( rm.getInfoGain() > INFO_GAIN_THRESHOLD
          && rm.isPositive() ) { // TODO handle negatives with "NOT"s?
          LOG.debug( "\t" + rm.getTerm() +
              " is relevant with an info gain of " + rm.getInfoGain() );
          relevant.add( rm.getTerm() );
          newRelevant.add( rm );
        } else {
          irrelevant.add( rm.getTerm() );
          // log why it's irrelevant
          if ( rm.getInfoGain() >= INFO_GAIN_THRESHOLD ) {
            LOG.debug( "\t" + rm.getTerm() +
                " (info gain: " + rm.getInfoGain() + ") " +
                " is irrelevant because it is negative." );
          } else {
            LOG.debug( "\t" + rm.getTerm() +
              " is irrelevant with an info gain of " + rm.getInfoGain() );
          }
        }
      }
    }

    LOG.info(  "Proposing terms: " + newRelevant );
    return newRelevant;
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  public void simulateClopidogrelReview()
    throws InterruptedException, IOException {
    // prepare the CSV output
    FileWriter fw = new FileWriter( "stats.csv" );
    BufferedWriter out = new BufferedWriter( fw );
   // header row
    out.write( "iteration,papers proposed," +
        "papers added,n cost,n recall,.75n cost,.75n recall,.5n cost," +
        ".5n recall,np cost,np recall" );
    out.newLine();
    out.flush();

    StringBuffer popQuery = new StringBuffer( clopidogrelReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( clopidogrelReview.getQueryIC() );

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );

    Set<String> expertRelevantMesh = new HashSet<>();
    Set<String> expertIrrelevantMesh = new HashSet<>();
    Set<Citation> expertRelevantPapers = new HashSet<>();
    Set<Citation> expertIrrelevantPapers = new HashSet<>();

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        clopidogrelReview, expertRelevantPapers );
    search( searcher );

    // gather initial statistics on the results
    TreeMultimap<Double, Citation> cosineMap = rank( searcher,
        expertRelevantPapers, expertIrrelevantPapers );
    evaluateQuery( searcher, cosineMap, expertRelevantPapers,
        expertIrrelevantPapers );

    int i = 0;
    int termsProposed = 0, papersProposed = 0;
    boolean termsRemaining = false, papersRemaining = true;
    while ( termsRemaining || papersRemaining ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      // --- RANK TERMS ---
      if ( i % 2 == 1 ) {
        Set<String> meshProposals = getMeshProposals( searcher,
            expertRelevantMesh, expertIrrelevantMesh, expertRelevantPapers );

        boolean queryChanged = false;
        if ( meshProposals.isEmpty() ) {
          LOG.info( "No new MeSH terms to propose." );
          termsRemaining = false;
        } else {
          termsRemaining = true;
          termsProposed += MESH_PROPOSALS_PER_ITERATION;
          Set<RankedMesh> newRelevant = proposeTerms( meshProposals,
              expertRelevantMesh, expertIrrelevantMesh );
          LOG.debug( "\t total relevant: " + expertRelevantMesh.size() );
          LOG.debug( "\t total irrelevant: " + expertIrrelevantMesh.size() );
          queryChanged = refineQuery( popQuery, icQuery, newRelevant );
        }

        if ( queryChanged ) { // only rerun if it's changed
          // this has changed the query itself, so we need to rerun the query
          searcher = null;
          searcher = new ParallelPubmedSearcher( "(" + popQuery + ") AND " +
            "(" + icQuery + ")", clopidogrelReview, expertRelevantPapers );
          search( searcher );
        } else {
          LOG.info( "Query not changed." );
        }
      } else {
      // --- RANK PAPERS ---
        Set<Citation> paperProposals = getPaperProposals( searcher, cosineMap,
            expertRelevantPapers, expertIrrelevantPapers );

        if ( paperProposals.isEmpty() ) {
          LOG.info( "No new papers to propose." );
          papersRemaining = false;
        } else {
          papersRemaining = true;
          papersProposed += PAPER_PROPOSALS_PER_ITERATION;
          int numRelevant = expertRelevantPapers.size();
          proposePapers( paperProposals,
            expertRelevantPapers, expertIrrelevantPapers );

          // make sure the sim calculations are done on the latest set
          if ( expertRelevantPapers.size() > numRelevant ) {
            searcher.updateSimilarities( expertRelevantPapers );
          }
          LOG.debug( "\t# relevant: " + expertRelevantPapers.size() );
          LOG.debug( "\t# irrelevant: " + expertIrrelevantPapers.size() );
          LOG.info( "# relevant papers: " + expertRelevantPapers.size() );
        }
      }

      // we have changed either the query or the ranking, so we need to
      // gather new statistics on the results
      cosineMap = rank(
          searcher, expertRelevantPapers, expertIrrelevantPapers );
      Map<String, InfoMeasure> im = evaluateQuery( searcher, cosineMap,
        expertRelevantPapers, expertIrrelevantPapers );

      if ( im != null ) {
        // write out the current stats
        double costL1 = ( .33 * termsProposed ) + papersProposed + activeReview
            .getRelevantLevel1().size() - im.get( "L1" ).getTruePositives();
        double costL2 = ( .33 * termsProposed ) + papersProposed + activeReview
            .getRelevantLevel1().size() - im.get( "L2" ).getTruePositives();
        out.write( i + "," + papersProposed + "," +
          expertRelevantPapers.size() +
          "," + costL1 + "," + im.get( "L1" ).getRecall() + "," + costL2 +
          "," + im.get( "L2" ).getRecall() );
      }

      out.newLine();
      out.flush();
    }

    out.close();
    fw.close();
  }
}
