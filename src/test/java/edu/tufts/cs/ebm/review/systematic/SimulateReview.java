package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.File;
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
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.avaje.ebean.Ebean;
import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.mesh.RankedMesh;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.PicoElement;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.text.BagOfWords;
import edu.tufts.cs.ml.text.CosineSimilarity;
import edu.tufts.cs.similarity.CachedCosineSimilarity;

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
  /** The Bag of Words. */
  protected BagOfWords<Integer> bow;
  /** The Cosine Similarity. */
  protected CosineSimilarity<Integer> cs;
  /** The positive class label. */
  protected static final int POS = 1;
  /** The negative class label. */
  protected static final int NEG = -1;

  /**
   * Evaluate the query.
   * @param searcher
   * @return
   */
  protected Map<String, InfoMeasure> evaluateQuery(
      TreeMultimap<Double, PubmedId> rankMap,
      Set<PubmedId> expertRelevantPapers,
      Set<PubmedId> expertIrrelevantPapers ) {

    if ( rankMap.size() == 0 ) {
      return null;
    }

    int i = 0;
    int truePosTotal = 0;
    int truePosL1 = 0;
    int truePosL2 = 0;
    
    for ( PubmedId pmid : expertRelevantPapers ) {
      if ( activeReview.getRelevantLevel2().contains( pmid ) ) {
        truePosL2++;
      }
      truePosL1++;
    }

    LOG.info( "\tTrue & false positives total: " + i );
    LOG.info( "\tTrue positives for L1/n: " + truePosL1 );
    LOG.info( "\tTrue positives for L2/n: " + truePosL2 );
    LOG.info( "\tTrue positives for all: " + truePosTotal );

    Map<String, InfoMeasure> infoMap = new HashMap<>();
    InfoMeasure l1Info = new InfoMeasure( truePosL1,
        activeReview.getRelevantLevel1().size() );
    InfoMeasure l2Info = new InfoMeasure( truePosL2,
        activeReview.getRelevantLevel2().size() );
    infoMap.put( "L1", l1Info );
    infoMap.put( "L2", l2Info );

    LOG.info( "Results for L1: " + l1Info );
    LOG.info( "Results for L2: " + l2Info );

    return infoMap;
  }

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  protected Set<PubmedId> getPaperProposals(
      TreeMultimap<Double, PubmedId> rankMap,
      Set<PubmedId> expertRelevantPapers,
      Set<PubmedId> expertIrrelevantPapers ) {
    Set<PubmedId> results = new HashSet<>();

    List<PubmedId> citList = new ArrayList<>();
//    out:
    for ( Double sim : rankMap.keySet().descendingSet() ) {
      for ( PubmedId pmid : rankMap.get( sim ) ) {
        if ( !expertRelevantPapers.contains( pmid ) &&
            !expertIrrelevantPapers.contains( pmid ) ) {
//          if ( results.size() < PAPER_PROPOSALS_PER_ITERATION ) {
//            results.add( pmid );
//          } else {
//            break out;
//          }
          citList.add( pmid );
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
  protected Set<PubmedId> proposePapers( Set<PubmedId> proposals,
      Set<PubmedId> relevant, Set<PubmedId> irrelevant ) {
    LOG.info( "Proposing papers..." );
    Set<PubmedId> newRelevant = new HashSet<>();
    for ( PubmedId pmid : proposals ) {
      if ( activeReview.getRelevantLevel1().contains( pmid ) ) {
        LOG.debug( "\t" + pmid + " is relevant" );
        newRelevant.add( pmid );
      } else {
        LOG.debug( "\t" + pmid + " is irrelevant" );
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
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, UnlabeledFeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {
    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();

    // train the bag of words
    for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
      bow.train( fv, POS );
    }
    for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
      bow.train( fv, NEG );
    }

    // "train" the cosine similarity with the relevant data
    cs.setCompareTo( bow.getTrainingData( POS ) );

    // test the remaining citations
    for ( PubmedId pmid : citations.keySet() ) {
      UnlabeledFeatureVector<Integer> ufv = citations.get( pmid ); 
      double sim = cs.calculateSimilarity( ufv );
      rankMap.put( sim, pmid );
    }

    // record the ranks
    recordRank( rankMap, expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

    return rankMap;
  }

  /**
   * Record the current ranking.
   * @param rankMap
   */
  protected void recordRank( TreeMultimap<Double, PubmedId> rankMap,
      Set<PubmedId> expertRelevantPapers,
      Set<PubmedId> expertIrrelevantPapers ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( rankMap.values().size() );
    }

    int rank = 1;
    for ( Double sim : rankMap.keySet().descendingSet() ) {
      for ( PubmedId pmid : rankMap.get( sim ) ) {
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

    Set<PubmedId> allObserved = new HashSet<>();
    allObserved.addAll( expertRelevantPapers );
    allObserved.addAll( expertIrrelevantPapers );
    for ( PubmedId pmid : allObserved ) {
      // only record the first time you see this
      String observStr = observOutput.get( pmid );
      if ( observStr == null ) {
        observStr = String.valueOf( iteration );
        observOutput.put( pmid, observStr );
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

    // initialize the cache
    try {
      defaultCache = JCS.getInstance( DEFAULT_CACHE_NAME );
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

    Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers = new HashMap<>();
    Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers = new HashMap<>();

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        activeReview );
    search( searcher );

    initializeClassifier( searcher.getCitations() );
    Map<PubmedId, UnlabeledFeatureVector<Integer>> citations =
        createFeatureVectors( searcher.getCitations() );
    
    // populate the relevant papers with the seed citations
    for ( Citation c : activeReview.getSeedCitations() ) {
      expertRelevantPapers.put( c.getPmid(), citations.get( c.getPmid() ) );
    }

    // gather initial statistics on the results
    TreeMultimap<Double, PubmedId> rankMap = rank( citations,
        expertRelevantPapers, expertIrrelevantPapers );
    evaluateQuery( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );

    int i = 0;
    int papersProposed = 0;
    boolean papersRemaining = true;
    Map<String, InfoMeasure> im = null;
    while ( papersRemaining ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<PubmedId> paperProposals = getPaperProposals( rankMap,
          expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

      int numRelevant = expertRelevantPapers.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        papersRemaining = false;
      } else {
        papersRemaining = true;
        papersProposed += PAPER_PROPOSALS_PER_ITERATION;
        Set<PubmedId> accepted = proposePapers( paperProposals, expertRelevantPapers.keySet(),
            expertIrrelevantPapers.keySet() );

        // update the relevant/irrelevant lists
        for ( PubmedId pmid : paperProposals ) {
          if ( accepted.contains( pmid ) ) {
            expertRelevantPapers.put( pmid, citations.get( pmid ) );
          } else {
            expertIrrelevantPapers.put( pmid, citations.get( pmid ) );
          }
        }

        LOG.debug( "\t# relevant: " + expertRelevantPapers.size() );
        LOG.debug( "\t# irrelevant: " + expertIrrelevantPapers.size() );
      }

      // if new papers are proposed, update the ranking
      if ( expertRelevantPapers.size() > numRelevant ) {
        rankMap = rank( citations, expertRelevantPapers,
            expertIrrelevantPapers );
      }
      if ( expertRelevantPapers.size() > numRelevant || im == null ) {
        im = evaluateQuery( rankMap, expertRelevantPapers.keySet(),
            expertIrrelevantPapers.keySet() );
      }

      if ( im != null ) {
        // write out the current stats
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
   * Turn the Citations into FeatureVectors.
   * @param citations
   * @return
   */
  protected Map<PubmedId, UnlabeledFeatureVector<Integer>> createFeatureVectors(
      Set<Citation> citations ) {
    // create the feature vectors
    Map<PubmedId, UnlabeledFeatureVector<Integer>> fvs = new HashMap<>();
    for ( Citation c : citations ) {
      fvs.put( c.getPmid(), bow.createUnlabeledFV( c.getPmid().toString(),
          c.getTitle() + " " + c.getAbstr() ) );
    }
    
    return fvs;
  }

  /**
   * Initialize the classifier.
   */
  protected void initializeClassifier( Set<Citation> citations ) {
    // initialize the bag of words
    bow = new BagOfWords<Integer>(
        new File( "src/main/resources/stoplists/en.txt" ) );
    for ( Citation c : activeReview.getSeedCitations() ) {
      // seed citations are in the positive class
      bow.train( c.getPmid().toString(), c.getTitle() + " " + c.getAbstr(), 1 );
    }
    
    // initialize the cosine similarity
    cs = new CachedCosineSimilarity<Integer>( defaultCache, bow.getTrainingData() );
    
    // create the features
    List<String> texts = new ArrayList<String>();
    for ( Citation c : citations ) {
      texts.add( c.getTitle() + " " + c.getAbstr() );
    }
    bow.createFeatures( texts );
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
}
