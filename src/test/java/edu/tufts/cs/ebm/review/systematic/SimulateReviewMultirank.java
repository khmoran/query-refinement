package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.rank.BordaAggregator;
import edu.tufts.cs.similarity.MeshClassifier;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewMultirank extends SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewMultirank.class );
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 50; // 5
  /** The portion of documents to observe. */
  protected static final double PERCENT_TO_OBSERVE = .9; // 1
  /** The weight (cost) given to reading a full paper. */
  protected static final double READ_PAPER_WEIGHT = 5;

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  protected Set<Citation> getPaperProposals( ParallelPubmedSearcher searcher,
      List<Citation> citList,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {
    Set<Citation> results = new HashSet<>();

    // WITHOUT REPLACEMENT:
//    List<Citation> unobserved = new ArrayList<>();
//    for ( Citation c : citList ) {
//      if ( !expertRelevantPapers.contains( c ) &&
//          !expertIrrelevantPapers.contains( c ) ) {
//        unobserved.add( c );
//      }
//    }
    
    // WITH REPLACEMENT:
    List<Citation> unobserved = citList;

    LOG.info( "Getting paper proposal set from " + unobserved.size() + " papers..." );
    Set<Integer> rands = MathUtil.uniqueHarmonicRandom(
        unobserved.size(), PAPER_PROPOSALS_PER_ITERATION );
    LOG.info( "\tGetting citations at ranks " + rands );
    for ( int r : rands ) {
      results.add( unobserved.get( r ) );
    }

    LOG.info(  "Paper proposals: " + results );
    return results;
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  @SuppressWarnings( "unchecked" )
  protected List<Citation> rank(
      ParallelPubmedSearcher searcher, Set<Citation> proposals,
      MeshClassifier meshClassifier ) {
    TreeMultimap<Double, Citation> cosineMap = TreeMultimap.create();
    TreeMultimap<Double, Citation> meshMap = TreeMultimap.create();

    for ( Citation c : searcher.getCitations() ) {
      cosineMap.put( c.getSimilarity(), c );
      meshMap.put( meshClassifier.classify( c ), c );
    }
    
    BordaAggregator<Citation> borda = new BordaAggregator<>();
    List<Citation> ranks = borda.aggregate( cosineMap, meshMap );
    
//    // -- TEMPORARY -- //
//    List<Citation> ranks = new ArrayList<>();
//    for ( Double d : cosineMap.keySet().descendingSet() ) {
//      for ( Citation c : cosineMap.get( d ) ) {
//        ranks.add( c );
//      }
//    }

    // record the ranks
    recordRank( ranks, proposals );

    return ranks;
  }

  /**
   * Record the current ranking.
   * @param cosineMap
   */
  protected void recordRank( List<Citation> ranks,
     Set<Citation> proposals ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( ranks.size() );
    }

    int rank = 1;
    for ( Citation c : ranks ) {
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
   * Propose the papers to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<Citation> proposePapers( Set<Citation> proposals ) {
    LOG.info( "Proposing papers..." );
    Set<Citation> newRelevant = new HashSet<>();
    for ( Citation c : proposals ) {
      if ( activeReview.getRelevantLevel1().contains( c.getPmid() ) ) {
        LOG.debug( "\t" + c.getTitle() + " [" + c.getPmid() + "] is relevant" );
        newRelevant.add( c );
      }
    }

    LOG.info( "Accepting papers: " + newRelevant );
    return newRelevant;
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
      "i,cost,#L2 observed,L2recall" );
    out.newLine();
    out.flush();

    StringBuffer popQuery = new StringBuffer( activeReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( activeReview.getQueryIC() );

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );

    Set<Citation> expertAllRelevant = new HashSet<>();
    Set<Citation> expertL2Relevant = new HashSet<>();
    Set<Citation> expertIrrelevantPapers = new HashSet<>();
    
    MeshClassifier meshClassifier = new MeshClassifier();

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        activeReview, expertL2Relevant );
    search( searcher );

    // gather initial statistics on the results
    List<Citation> ranks = rank( searcher,
        new HashSet<Citation>(), meshClassifier );
    
    int numPapersToObserve = (int) Math.ceil(
        searcher.getCitations().size() * PERCENT_TO_OBSERVE );
    
    LOG.info( "Observing at least " + PERCENT_TO_OBSERVE +
        " of available papers ("+ numPapersToObserve + ")" );

    int i = 0;
    int numPapersRead = 0;
    while ( ( expertAllRelevant.size() + expertIrrelevantPapers.size() )
        < numPapersToObserve ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<Citation> paperProposals = getPaperProposals( searcher, ranks,
          expertAllRelevant, expertIrrelevantPapers );

      int numRelevant = expertAllRelevant.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        break;
      } else {
        Set<Citation> accepted = proposePapers( paperProposals );
        
        for ( Citation c : paperProposals ) {
          if ( accepted.contains( c ) ) {
            expertAllRelevant.add( c );
            
            // --- TEMPORARY --- //
            if ( activeReview.getRelevantLevel2().contains( c.getPmid() ) ) {
              expertL2Relevant.add( c );
            }
            // --- TEMPORARY --- //
          } else {
            expertIrrelevantPapers.add( c );
          }
        }

        // if new papers are accepted, update the similarities, infoGain,
        // and rankings
        if ( expertAllRelevant.size() > numRelevant ) {
          searcher.updateSimilarities( expertAllRelevant ); // update cosine
          Set<Citation> l1s = new HashSet<>( expertAllRelevant );
          l1s.removeAll( expertL2Relevant );
          if ( expertL2Relevant.size() > 0 ) {
            //meshClassifier.update( expertIrrelevantPapers, expertL2Relevant ); // update mesh - 2
            //meshClassifier.update( expertIrrelevantPapers, expertAllRelevant ); // update mesh - 3
            meshClassifier.update( expertL2Relevant, l1s ); // update mesh - 1 BACKWARDS
          }
          ranks = rank( searcher, paperProposals, meshClassifier ); // rank
        } else { // either way record the ranks
          recordRank( ranks, paperProposals );
        }

        LOG.debug( "\t# total relevant observed: " + expertAllRelevant.size() );
        LOG.debug( "\t# total L2 observed: " + expertL2Relevant.size() );
        LOG.debug( "\t# irrelevant observed: " + expertIrrelevantPapers.size() );
      }

      //Map<String, InfoMeasure> im = evaluateQuery( searcher, ranks, expertAllRelevant,
      //    expertIrrelevantPapers );
      // write out the current stats
      InfoMeasure im = new InfoMeasure( expertL2Relevant.size(),
          activeReview.getRelevantLevel2().size() );
      LOG.info( "\tL2 recall: " + MathUtil.round( im.getRecall(), 4) );
      // the cost of reading all of the titles + abstracts so far
      double costL1 = expertAllRelevant.size() + expertIrrelevantPapers.size();
      // the cost of reading the full papers so far
      double costL2 = READ_PAPER_WEIGHT * numPapersRead;
      out.write( i + "," + (costL1 + costL2) + "," +
        expertL2Relevant.size() + "," + im.getRecall() );

      out.newLine();
      out.flush();
    }

    out.close();
    fw.close();
  }

  /**
   * Evaluate the query.
   * @param searcher
   * @return
   */
  /*protected Map<String, InfoMeasure> evaluateQuery(
      ParallelPubmedSearcher searcher, List<Citation> ranks,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {

    int i = 0;
    int truePosTotal = 0;
    int truePosL1 = 0;
    int truePosL2 = 0;
    int l2ExpertRel = 0;
    TreeMap<Integer, Integer> iMap = new TreeMap<>();
    for ( Citation c : ranks ) {
      if ( activeReview.getRelevantLevel1().contains( c.getPmid() ) ) {
        truePosTotal++;
        if ( !expertRelevantPapers.contains( c ) ) {
          if ( i < activeReview.getRelevantLevel1().size() ) {
            truePosL1++;

            if ( //i < activeReview.getRelevantLevel2().size() &&
                activeReview.getRelevantLevel2().contains( c.getPmid() ) ) {
              truePosL2++;
            }
          }
        } else {
          if ( activeReview.getRelevantLevel2().contains( c.getPmid() ) ) {
            l2ExpertRel++;
          }
        }
      }
      iMap.put( i, truePosTotal );
      if ( !expertRelevantPapers.contains( c ) &&   // want to pretend
          !expertIrrelevantPapers.contains( c ) ) { // these aren't in
        i++; // the list so only increment for unobserved papers
      }
    }

    Map<String, InfoMeasure> infoMap = new HashMap<>();
    // we subtract the true pos #s below to get the number in the top n
    // that will still have to be reviewed by the researcher
    InfoMeasure l1Info = calculateInfoMeasureL1( activeReview,
        truePosL1 + expertRelevantPapers.size() );
    InfoMeasure l2Info = calculateInfoMeasureL2( activeReview,
        truePosL2 + l2ExpertRel );
    InfoMeasure totalInfo = new InfoMeasure( truePosTotal,
        activeReview.getRelevantLevel1().size() );
    infoMap.put( "L1", l1Info );
    infoMap.put( "L2", l2Info );

    LOG.info( "Results for L1: " + l1Info );
    LOG.info( "Results for L2: " + l2Info );
    LOG.info( "Results for all: " + totalInfo );

    for ( int j : iMap.keySet() ) {
      int truePos = iMap.get( j );

      if ( truePos == truePosTotal ) {
        LOG.info( "Minimum n for full recall: " + j );
        break;
      }
    }

    return infoMap;
  }*/

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
