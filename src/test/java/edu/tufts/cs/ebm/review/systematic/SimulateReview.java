package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.aliasi.spell.TfIdfDistance;
import com.aliasi.tokenizer.EnglishStopTokenizerFactory;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.avaje.ebean.Ebean;
import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.mesh.RankedMesh;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.PicoElement;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.similarity.CosineSimilarity;

/**
 * Test the MeshWalker class.
 */
public class SimulateReview extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReview.class );
  /** The number of minutes after which to time out the request. */
  protected static final int TIMEOUT_MINS = 60;
  /** The number of threads to fork off at a time. */
  protected static final int NUM_FORKS = 8;
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 5;
  /** The title TF-IDF instance. */
  protected static TfIdfDistance tfIdf;
  /** The cache name. */
  protected static final String DEFAULT_CACHE_NAME = "default";
  /** The data cache. */
  protected static JCS defaultCache;
  /** The file containing the recall statistics. */
  protected String statsFile;
  /** The file containing the rankings of the papers. */
  protected String paperRankFile;
  /** The file containing the probabilities of the papers. */
  protected String paperProbFile;
  /** The rankings output. */
  protected Map<PubmedId, String> rankOutput = new HashMap<>();
  /** The observations output. */
  protected Map<PubmedId, String> observOutput = new HashMap<>();
  /** The probabilities output. */
  protected Map<PubmedId, String> probOutput = new HashMap<>();
  /** The iteration. */
  protected long iteration = 0;
  /** The z value for probability calculations. */
  protected double z = -1;
  /** The active review. */
  protected SystematicReview activeReview;

  /**
   * Evaluate the query.
   * @param searcher
   * @return
   */
  protected Map<String, InfoMeasure> evaluateQuery(
      ParallelPubmedSearcher searcher, TreeMultimap<Double, Citation> cosineMap,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {

    if ( cosineMap.size() == 0 ) {
      return null;
    }

    LOG.info( "\tSimilarity range: [" + MathUtil.round(
        cosineMap.keySet().first(), 4 ) + ", " +
      MathUtil.round( cosineMap.keySet().last(), 4 ) + "]" );

    int i = 0;
    int truePosTotal = 0;
    int truePosL1 = 0;
    int truePosL2 = 0;
    int l2ExpertRel = 0;
    TreeMap<Integer, Integer> iMap = new TreeMap<>();
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( Citation c : cosineMap.get( sim ) ) {
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
    }

    LOG.info( "\tTrue & false positives total: " + i );
    LOG.info( "\tTrue positives for L1/n: " + truePosL1 );
    LOG.info( "\tTrue positives for L2/n: " + truePosL2 );
    LOG.info( "\tTrue positives for all: " + truePosTotal );

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
  }

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  protected Set<Citation> getPaperProposals( ParallelPubmedSearcher searcher,
      TreeMultimap<Double, Citation> cosineMap,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {
    Set<Citation> results = new HashSet<>();

    List<Citation> citList = new ArrayList<>();
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( Citation c : cosineMap.get( sim ) ) {
        if ( !expertRelevantPapers.contains( c ) &&
            !expertIrrelevantPapers.contains( c ) ) {
          citList.add( c );
        }
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
   * Propose the papers to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<Citation> proposePapers( Set<Citation> proposals,
      Set<Citation> relevant, Set<Citation> irrelevant ) {
    LOG.info( "Proposing papers..." );
    Set<Citation> newRelevant = new HashSet<>();
    for ( Citation c : proposals ) {
      if ( activeReview.getRelevantLevel1().contains( c.getPmid() ) ) {
        LOG.debug( "\t" + c.getTitle() + " [" + c.getPmid() + "] is relevant" );
        relevant.add( c );
        newRelevant.add( c );
      } else {
        LOG.debug( "\t" + c.getTitle() + " [" + c.getPmid() +
            "] is irrelevant" );
        irrelevant.add( c );
      }
    }

    LOG.info( "Proposing papers: " + newRelevant );
    return newRelevant;
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  protected TreeMultimap<Double, Citation> rank(
      ParallelPubmedSearcher searcher, Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {
    TreeMultimap<Double, Citation> cosineMap = TreeMultimap.create();
    for ( Citation c : searcher.getCitations() ) {
      cosineMap.put( c.getSimilarity(), c );
    }

    // record the ranks
    recordRank( cosineMap, expertRelevantPapers, expertIrrelevantPapers );

    return cosineMap;
  }

  /**
   * Record the current ranking.
   * @param cosineMap
   */
  protected void recordRank( TreeMultimap<Double, Citation> cosineMap,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {

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

    Set<Citation> allObserved = new HashSet<>();
    allObserved.addAll( expertRelevantPapers );
    allObserved.addAll( expertIrrelevantPapers );
    for ( Citation c : allObserved ) {
      // only record the first time you see this
      String observStr = observOutput.get( c.getPmid() );
      if ( observStr == null ) {
        observStr = String.valueOf( iteration );
        observOutput.put( c.getPmid(), observStr );
      }
    }

    iteration++;
  }

  /**
   * Refine the query.
   * @param popQuery
   * @param icQuery
   * @param newRelevant
   */
  protected boolean refineQuery( StringBuffer popQuery, StringBuffer icQuery,
      Set<RankedMesh> newRelevant ) {
    LOG.info( "Refining query with new MeSH terms..." );
    boolean popChanged = false;
    boolean icChanged = false;
    for ( RankedMesh rm : newRelevant ) {
      if ( rm.getPico() == PicoElement.POPULATION ) {
        popQuery.append( " OR " + rm.getTerm() );
        popChanged = true;
      } else if ( rm.getPico() == PicoElement.INTERVENTION ) {
        icQuery.append( " OR " + rm.getTerm() );
        icChanged = true;
      } else {
        LOG.warn( "Non-population, non-intervention PICO element: " + rm );
      }
    }

    if ( popChanged ) {
      LOG.info( "Refined population query: " + popQuery );
    } else {
      LOG.info( "Population query not changed." );
    }

    if ( icChanged ) {
      LOG.info( "Refined intervention query: " + icQuery );
    } else {
      LOG.info( "Intervention query not changed." );
    }

    return popChanged || icChanged;
  }

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    if ( this.getClass().getSimpleName().contains(
        "ProtonBeam" ) ) {
      this.activeReview = protonBeamReview;
    } else if ( this.getClass().getSimpleName().contains(
        "Clopidogrel" ) ) {
      this.activeReview = clopidogrelReview;
    } else {
      LOG.error( "Could not determine review. Exiting." );
      System.exit( 1 );
    }

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    LOG.info( "# seeds:\t " + activeReview.getSeeds().size() );
    LOG.info( "# relevant L1:\t " + activeReview.getRelevantLevel1().size() );
    LOG.info( "# relevant L2:\t " + activeReview.getRelevantLevel2().size() );
    LOG.info( "# blacklisted:\t " + activeReview.getBlacklist().size() );

    // load up the relevant papers
    for ( PubmedId pmid : activeReview.getRelevantLevel2() ) {
      Ebean.find( PubmedId.class, pmid.getValue() );
    }

    TokenizerFactory tokenizerFactory = new EnglishStopTokenizerFactory(
        IndoEuropeanTokenizerFactory.INSTANCE );
    tfIdf = new TfIdfDistance( tokenizerFactory );

    // train the classifier
    for ( Citation seed : activeReview.getSeedCitations() ) {
      tfIdf.handle( seed.getTitle() );
      tfIdf.handle( seed.getAbstr() );
      //tfIdf.handle( seed.getMeshTerms().toString() );
    }

    // initialize the cache
    try {
      defaultCache = JCS.getInstance( DEFAULT_CACHE_NAME );
//      this.defaultCache.clear();
    } catch ( CacheException e ) {
      LOG.error( "Error intitializing prefetching cache.", e );
      e.printStackTrace();
    }
  }

  /**
   * Calculate z.
   * @param n
   * @return
   */
  protected double calcZ( int n ) {
    double z = 0;
    for ( int i = 1; i <= n; i++ ) {
      z += 1/(double) ( i );
    }
    
    return z;
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
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
        expertRelevantPapers, expertIrrelevantPapers );
    evaluateQuery( searcher, cosineMap, expertRelevantPapers,
        expertIrrelevantPapers );

    int i = 0;
    int papersProposed = 0;
    boolean papersRemaining = true;
    Map<String, InfoMeasure> im = null;
    while ( papersRemaining ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<Citation> paperProposals = getPaperProposals( searcher, cosineMap,
          expertRelevantPapers, expertIrrelevantPapers );

      int numRelevant = expertRelevantPapers.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        papersRemaining = false;
      } else {
        papersRemaining = true;
        papersProposed += PAPER_PROPOSALS_PER_ITERATION;
        proposePapers( paperProposals,
          expertRelevantPapers, expertIrrelevantPapers );

        // make sure the sim calculations are done on the latest set
        if ( expertRelevantPapers.size() > numRelevant ) {
          searcher.updateSimilarities( expertRelevantPapers );
        }
        LOG.debug( "\t# relevant: " + expertRelevantPapers.size() );
        LOG.debug( "\t# irrelevant: " + expertIrrelevantPapers.size() );
      }

      // if new papers are proposed, update the ranking
      if ( expertRelevantPapers.size() > numRelevant ) {
        cosineMap = rank(
          searcher, expertRelevantPapers, expertIrrelevantPapers );
      }
      if ( expertRelevantPapers.size() > numRelevant || im == null ) {
        im = evaluateQuery( searcher, cosineMap,
          expertRelevantPapers, expertIrrelevantPapers );
      }

      if ( im != null ) {
        // write out the current stats
        System.out.println( "Writing L2 recall: " + im.get( "L2" ).getRecall() );
        double costL1 = papersProposed + activeReview
            .getRelevantLevel1().size() - im.get( "L1" ).getTruePositives();
        double costL2 = papersProposed + activeReview
            .getRelevantLevel1().size() - im.get( "L2" ).getTruePositives();
        out.write( i + "," + papersProposed + "," +
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
    FileWriter fstreamRanks = new FileWriter( paperRankFile );
    BufferedWriter outRanks = new BufferedWriter( fstreamRanks );

    FileWriter fstreamProbs = new FileWriter( paperProbFile );
    BufferedWriter outProbs = new BufferedWriter( fstreamProbs );

    StringBuffer header = new StringBuffer(
        "pmid,L1 inclusion,L2 inclusion,iteration(s) observed," );
    for ( int i = 1; i <= iteration; i++ ) {
      header.append( i + "," );
    }
    outRanks.append( header.toString() + "\n" );
    outProbs.append( header.toString() + "\n" );
    
    for ( PubmedId pmid : rankOutput.keySet() ) {
      String observ = observOutput.get( pmid );
      String rankStr = rankOutput.get( pmid );
      String observStr = ( observ == null ) ? "" : observ;
      String l1 = activeReview.getRelevantLevel1().contains( pmid ) ? "true" : "false";
      String l2 = activeReview.getRelevantLevel2().contains( pmid ) ? "true" : "false";
      outRanks.append( pmid + "," + l1 + "," + l2 + ",\"" + observStr + "\"," + rankStr + "\n" );
      
      String prob = probOutput.get( pmid );
      String probStr = ( prob == null ) ? "" : prob;
      outProbs.append( pmid + ",,,," + probStr + "\n" );
    }
    
    outRanks.close();
    outProbs.close();
  }
  

  /**
   * Update the similarity values.
   */
  public void updateSimilarities( Set<Citation> citations, Set<Citation> relevant ) {
    LOG.debug( "Comparing " + citations.size() + " citations to " +
        relevant.size() + " papers for similarity." );

    // parallelized
    ExecutorService executorService = Executors.newFixedThreadPool( NUM_FORKS );
    for ( Citation c : citations ) {
      CosineSimilarity cs = new CosineSimilarity( defaultCache,
          tfIdf, c, relevant, activeReview );
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
