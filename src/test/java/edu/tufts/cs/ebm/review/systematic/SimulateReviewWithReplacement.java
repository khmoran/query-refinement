package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.util.MathUtil;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewWithReplacement extends SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewWithReplacement.class );
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 50;
  /** The portion of documents to observe. */
  protected static final double PERCENT_TO_OBSERVE = .5;

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  @Override
  protected Set<Citation> getPaperProposals( ParallelPubmedSearcher searcher,
      TreeMultimap<Double, Citation> cosineMap,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {
    Set<Citation> results = new HashSet<>();

    List<Citation> citList = new ArrayList<>();
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( Citation c : cosineMap.get( sim ) ) {
        // WITH REPLACEMENT:
          citList.add( c );
      }
    }

    LOG.info( "Getting paper proposal set..." );
    Set<Integer> rands = MathUtil.uniqueHarmonicRandom(
        citList.size(), PAPER_PROPOSALS_PER_ITERATION );
    //Set<Integer> rands = MathUtil.uniqueQuadraticRandom(
    //    n, PAPER_PROPOSALS_PER_ITERATION );
    LOG.info( "\tGetting citations at ranks " + rands );
    for ( int r : rands ) {
      results.add( citList.get( r ) );
    }

    LOG.info(  "Paper proposals: " + results );
    return results;
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  protected TreeMultimap<Double, Citation> rank(
      ParallelPubmedSearcher searcher, Set<Citation> proposals ) {
    TreeMultimap<Double, Citation> cosineMap = TreeMultimap.create();
    for ( Citation c : searcher.getCitations() ) {
      cosineMap.put( c.getSimilarity(), c );
    }

    // record the ranks
    recordRank( cosineMap, proposals );

    return cosineMap;
  }

  /**
   * Record the current ranking.
   * @param cosineMap
   */
  protected void recordRank( TreeMultimap<Double, Citation> cosineMap,
     Set<Citation> proposals ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( cosineMap.values().size() );
    }

    int rank = 1;
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( Citation c : cosineMap.get( sim ) ) {
        PubmedId pmid = c.getPmid();
        
        String rankStr = rankOutput.get( pmid );
        if ( rankStr == null ) {
          rankStr = "";
        } else {
          rankStr += ",";
        }
        rankStr += String.valueOf( rank );
        rankOutput.put( pmid, rankStr );

        double prob = (double) 1/ (double) rank / z;
        prob = MathUtil.round( prob, 7 );

        String probStr = probOutput.get( pmid );
        if ( probStr == null ) {
          probStr = "";
        } else {
          probStr += ",";
        }
        probStr += String.valueOf( prob );
        probOutput.put( pmid, probStr );

        rank++;
      }
    }

    for ( Citation c : proposals ) {
      String observStr = observOutput.get( c.getPmid() );
      if ( observStr == null ) {
        observStr = "";
      } else {
        observStr += ",";
      }
      observStr += String.valueOf( iteration );
      observOutput.put( c.getPmid(), observStr );
    }

    iteration++;
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  @Override
  public void simulateReview()
    throws InterruptedException, IOException {
    // prepare the CSV output
    FileWriter fw = new FileWriter( statsFile );
    BufferedWriter out = new BufferedWriter( fw );
   // header row
    out.write(
      "i,papers proposed,papers added,L1 cost,L1 recall,L2cost,L2recall" );
    out.newLine();
    out.flush();

    StringBuffer popQuery = new StringBuffer( activeReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( activeReview.getQueryIC() );

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );

    Set<Citation> expertRelevantPapers = new HashSet<>();
    Set<Citation> expertIrrelevantPapers = new HashSet<>();

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        activeReview, expertRelevantPapers );
    search( searcher );

    // gather initial statistics on the results
    TreeMultimap<Double, Citation> cosineMap = rank( searcher,
        new HashSet<Citation>() );
    evaluateQuery( searcher, cosineMap, expertRelevantPapers,
        expertIrrelevantPapers );
    
    int numPapersToObserve = (int) Math.ceil(
        searcher.getCitations().size() * PERCENT_TO_OBSERVE );
    
    LOG.info( "Observing at least " + PERCENT_TO_OBSERVE +
        " of available papers ("+ numPapersToObserve + ")" );

    int i = 0;
    while ( ( expertRelevantPapers.size() + expertIrrelevantPapers.size() )
        < numPapersToObserve ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<Citation> paperProposals = getPaperProposals( searcher, cosineMap,
          expertRelevantPapers, expertIrrelevantPapers );

      int numRelevant = expertRelevantPapers.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        break;
      } else {
        Set<Citation> newRelevant = proposePapers( paperProposals,
          expertRelevantPapers, expertIrrelevantPapers );

        double observedRel = (double) newRelevant.size() / (double) paperProposals.size();
        double expectedRel = (double) ( activeReview.getRelevantLevel1().size() - expertRelevantPapers.size() )
            / ( (double) searcher.getCitations().size() - expertRelevantPapers.size() - expertIrrelevantPapers.size() );
        LOG.info( "% observed relevant: " + observedRel );
        LOG.info( "%cR expected relevant: " + expectedRel );

        // make sure the sim calculations are done on the latest set
        if ( expertRelevantPapers.size() > numRelevant ) {
          searcher.updateSimilarities( expertRelevantPapers );
        }
        LOG.debug( "\t# relevant: " + expertRelevantPapers.size() );
        LOG.debug( "\t# irrelevant: " + expertIrrelevantPapers.size() );

        // if new papers are proposed, update the ranking
        if ( expertRelevantPapers.size() > numRelevant ) {
          cosineMap = rank( searcher, paperProposals );
        } else { // but either way record the ranks
          recordRank( cosineMap, paperProposals );
        }
      }

      Map<String, InfoMeasure> im = evaluateQuery( searcher, cosineMap,
        expertRelevantPapers, expertIrrelevantPapers );

      if ( im != null ) {
        // write out the current stats
        int observed = expertRelevantPapers.size() + expertIrrelevantPapers.size();
        double costL1 = observed + activeReview
            .getRelevantLevel1().size() - im.get( "L1" ).getTruePositives();
        double costL2 = observed + activeReview
            .getRelevantLevel1().size() - im.get( "L2" ).getTruePositives();
        out.write( i + "," + observed + "," +
          expertRelevantPapers.size() +
          "," + costL1 + "," + im.get( "L1" ).getRecall() +
          "," + costL2 + "," + im.get( "L2" ).getRecall() );
      }

      out.newLine();
      out.flush();
    }

    out.close();
    fw.close();
  }

  /**
   * Tear down the test harness.
   * @throws IOException
   * @throws WriteException
   */
  @AfterSuite
  public void tearDown() throws IOException {
    super.tearDown();
  }
}
