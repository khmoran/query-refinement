package edu.tufts.cs.ebm.review.systematic.simulation.online.repl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.simulation.online.OnlineSimulator;
import edu.tufts.cs.ebm.util.MathUtil;

/**
 * Test the MeshWalker class.
 */
public abstract class OnlineSimulationReplacement<I, C> extends
  OnlineSimulator<I, C> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulationReplacement.class );
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 50;
  /** The portion of documents to observe. */
  protected static final double PERCENT_TO_OBSERVE = .5;
  
  public OnlineSimulationReplacement( String dataset ) throws Exception {
     super( dataset );
  }

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  @Override
  protected Set<I> getPaperProposals( 
      TreeMultimap<Double, I> rankMap,
      Set<I> expertRelevantPapers,
      Set<I> expertIrrelevantPapers ) {
    Set<I> results = new HashSet<>();

    List<I> citList = new ArrayList<>();
    for ( Double sim : rankMap.keySet().descendingSet() ) {
      for ( I pmid : rankMap.get( sim ) ) {
        // WITH REPLACEMENT:
          citList.add( pmid );
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

  @Override
  protected void recordRank( TreeMultimap<Double, I> rankMap,
     Set<I> proposals, Set<I> expertIrrelevantPapers ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( rankMap.values().size() );
    }

    int rank = 1;
    for ( Double sim : rankMap.keySet().descendingSet() ) {
      for ( I pmid : rankMap.get( sim ) ) {
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

    for ( I pmid : proposals ) {
      String observStr = observOutput.get( pmid );
      if ( observStr == null ) {
        observStr = "";
      } else {
        observStr += ",";
      }
      observStr += String.valueOf( iteration );
      observOutput.put( pmid, observStr );
    }

    iteration++;
  }

  @SuppressWarnings( "unchecked" )
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

    Map<I, C> expertRelevantPapers = new HashMap<>();
    Map<I, C> expertIrrelevantPapers = new HashMap<>();

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        activeReview );
    search( searcher );

    initializeClassifier( searcher.getCitations() );
    Map<I, C> citations =
        createFeatureVectors( searcher.getCitations() );

    // populate the relevant papers with the seed citations
    for ( Citation c : activeReview.getSeedCitations() ) {
      expertRelevantPapers.put( (I) c.getPmid(), citations.get( c.getPmid() ) );
    }

    // gather initial statistics on the results
    TreeMultimap<Double, I> rankMap = rank( citations,
        expertRelevantPapers, expertIrrelevantPapers );
    evaluateQuery( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );
    
    int numPapersToObserve = (int) Math.ceil(
        searcher.getCitations().size() * PERCENT_TO_OBSERVE );
    
    LOG.info( "Observing at least " + PERCENT_TO_OBSERVE +
        " of available papers ("+ numPapersToObserve + ")" );

    int i = 0;
    while ( ( expertRelevantPapers.size() + expertIrrelevantPapers.size() )
        < numPapersToObserve ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<I> paperProposals = getPaperProposals( rankMap,
          expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

      int numRelevant = expertRelevantPapers.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        break;
      } else {
        Set<I> accepted = proposePapers( paperProposals,
          expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );
        
        // update the relevant/irrelevant lists
        for ( I pmid : paperProposals ) {
          if ( accepted.contains( pmid ) ) {
            expertRelevantPapers.put( pmid, citations.get( pmid ) );
          } else {
            expertIrrelevantPapers.put( pmid, citations.get( pmid ) );
          }
        }

        double observedRel = (double) accepted.size() / (double) paperProposals.size();
        double expectedRel = (double) ( activeReview.getRelevantLevel1().size() - expertRelevantPapers.size() )
            / ( (double) searcher.getCitations().size() - expertRelevantPapers.size() - expertIrrelevantPapers.size() );
        LOG.info( "% observed relevant: " + observedRel );
        LOG.info( "%cR expected relevant: " + expectedRel );

        LOG.debug( "\t# relevant: " + expertRelevantPapers.size() );
        LOG.debug( "\t# irrelevant: " + expertIrrelevantPapers.size() );

        // if new papers are proposed, update the ranking
        if ( expertRelevantPapers.size() > numRelevant ) {
          rankMap = rank( citations,
              expertRelevantPapers, expertIrrelevantPapers );
        } else { // but either way record the ranks
          recordRank( rankMap, paperProposals, new HashSet<I>() );
        }
      }

      Map<String, InfoMeasure> im = evaluateQuery( rankMap,
        expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

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
}
