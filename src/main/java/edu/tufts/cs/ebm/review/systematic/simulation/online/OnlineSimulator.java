package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import com.avaje.ebean.Ebean;
import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.mesh.RankedMesh;
import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.ParallelPubmedSearcher;
import edu.tufts.cs.ebm.refinement.query.PicoElement;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.review.systematic.simulation.Simulator;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.util.MalletConverter;
import edu.tufts.cs.ml.util.Util;

/**
 * An online simulation of a systematic review.
 */
public abstract class OnlineSimulator<I, C> extends Simulator {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulator.class );
  /** The rankings output. */
  protected Map<I, String> rankOutput = new HashMap<>();
  /** The observations output. */
  protected Map<I, String> observOutput = new HashMap<>();
  /** The probabilities output. */
  protected Map<I, String> probOutput = new HashMap<>();
  /** The active review. */
  protected SystematicReview activeReview;

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  public OnlineSimulator( String review ) throws Exception {
    Collection<SystematicReview> reviews = reviews();
    String norm = Util.normalize( review );
    for ( SystematicReview r : reviews ) {
      if ( Util.normalize( r.getName() ).contains( norm ) ) {
        this.activeReview = r;
      }
    }
    
    if ( this.activeReview == null ) throw new RuntimeException(
        "Could not find review " + review );

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    LOG.info( "# seeds:\t " + activeReview.getSeeds().size() );
    LOG.info( "# relevant L1:\t " + activeReview.getRelevantLevel1().size() );
    LOG.info( "# relevant L2:\t " + activeReview.getRelevantLevel2().size() );
    LOG.info( "# blacklisted:\t " + activeReview.getBlacklist().size() );

    // load up the seeds
    for ( PubmedId pmid : activeReview.getSeeds() ) {
      Ebean.find( PubmedId.class, pmid.getValue() ); // load the seeds
    }
    
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
   * Turn the Citations into FeatureVectors.
   * @param citations
   * @return
   */
  protected abstract Map<I, C> createFeatureVectors(
      Set<Citation> citations );
  

  /**
   * Get the training data in MALLET form.
   * @param relevant
   * @param irrelevant
   * @return
   */
  protected InstanceList createTrainingData( InstanceList instances, 
      Collection<PubmedId> relevant, Collection<PubmedId> irrelevant ) {
    InstanceList il = instances.shallowClone();
    
    Collection<Instance> toRemove = new ArrayList<Instance>();
    for ( Instance i : il ) {
      try {
        PubmedId pmid = new PubmedId( i.getName().toString() );
        if ( !( relevant.contains( pmid ) || irrelevant.contains( pmid ) ) ) {
            toRemove.add( i );
        }
      } catch ( NumberFormatException | ParseException e ) {
        LOG.error( e );
      }
    }
    il.removeAll( toRemove );

    return il;
  }

  /**
   * Evaluate the query.
   * @param searcher
   * @return
   */
  protected Map<String, InfoMeasure> evaluateQuery(
      TreeMultimap<Double, I> rankMap,
      Set<I> expertRelevantPapers,
      Set<I> expertIrrelevantPapers ) {

    if ( rankMap.size() == 0 ) {
      return null;
    }

    int i = 0;
    int truePosTotal = 0;
    int truePosL1 = 0;
    int truePosL2 = 0;
    
    for ( I pmid : expertRelevantPapers ) {
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
  protected Set<I> getPaperProposals(
      TreeMultimap<Double, I> rankMap,
      Set<I> expertRelevantPapers,
      Set<I> expertIrrelevantPapers ) {
    Set<I> results = new HashSet<>();

    List<I> citList = new ArrayList<>();
    for ( Double sim : rankMap.keySet().descendingSet() ) {
      for ( I pmid : rankMap.get( sim ) ) {
        if ( !expertRelevantPapers.contains( pmid ) &&
            !expertIrrelevantPapers.contains( pmid ) ) {
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
   * Initialize the classifier.
   */
  protected abstract void initializeClassifier( Set<Citation> citations );

  /**
   * Initialize the Mallet vectors.
   * @param fvs
   */
  protected InstanceList initializeMallet( Collection<FeatureVector<Integer>> fvs ) {
    Metadata m = new Metadata();
    TrainRelation<Integer> train = new TrainRelation<Integer>( "train", m );
    
    for ( FeatureVector<Integer> fv : fvs ) {
      for( String feat : fv.keySet() ) {
        if ( !m.containsKey( feat ) ) {
          m.put( feat, "Object" );
        }
      }

      LabeledFeatureVector<Integer> lfv;
      try {
        PubmedId pmid = new PubmedId( fv.getId() );
        if ( activeReview.getRelevantLevel1().contains( pmid ) 
          || activeReview.getRelevantLevel2().contains( pmid ) ) {
          lfv = new LabeledFeatureVector<Integer>( POS, fv.getId() );
        } else {
          lfv = new LabeledFeatureVector<Integer>( NEG, fv.getId() );
        }
        lfv.putAll( fv );
        train.add( lfv );
      } catch ( NumberFormatException | ParseException e ) {
        LOG.error( e );
      }
    }

    return MalletConverter.convert( train );
  }

  /**
   * Propose the papers to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<I> proposePapers( Set<I> proposals,
      Set<I> relevant, Set<I> irrelevant ) {
    LOG.info( "Proposing papers..." );
    Set<I> newRelevant = new HashSet<>();
    for ( I pmid : proposals ) {
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
  protected abstract TreeMultimap<Double, I> rank(
      Map<I, C> citations,
      Map<I, C> expertRelevantPapers,
      Map<I, C> expertIrrelevantPapers );

  /**
   * Record the current ranking.
   * @param rankMap
   */
  protected void recordRank( TreeMultimap<Double, I> rankMap,
      Set<I> expertRelevantPapers,
      Set<I> expertIrrelevantPapers ) {

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

    Set<I> allObserved = new HashSet<>();
    allObserved.addAll( expertRelevantPapers );
    allObserved.addAll( expertIrrelevantPapers );
    for ( I pmid : allObserved ) {
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
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Override
  @SuppressWarnings( "unchecked" )
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
      if ( citations.get( c.getPmid() ) != null ) {
        expertRelevantPapers.put( (I) c.getPmid(), citations.get( c.getPmid() ) );
      }
    }

    // gather initial statistics on the results
    TreeMultimap<Double, I> rankMap = rank( citations,
        expertRelevantPapers, expertIrrelevantPapers );
    // record the ranks
    recordRank( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );
    evaluateQuery( rankMap, expertRelevantPapers.keySet(),
        expertIrrelevantPapers.keySet() );

    int i = 0;
    int papersProposed = 0;
    boolean papersRemaining = true;
    Map<String, InfoMeasure> im = null;
    while ( papersRemaining ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<I> paperProposals = getPaperProposals( rankMap,
          expertRelevantPapers.keySet(), expertIrrelevantPapers.keySet() );

      int numRelevant = expertRelevantPapers.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        papersRemaining = false;
      } else {
        papersRemaining = true;
        papersProposed += PAPER_PROPOSALS_PER_ITERATION;
        Set<I> accepted = proposePapers( paperProposals, expertRelevantPapers.keySet(),
            expertIrrelevantPapers.keySet() );

        // update the relevant/irrelevant lists
        for ( I pmid : paperProposals ) {
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
        // record the ranks
        recordRank( rankMap, expertRelevantPapers.keySet(),
            expertIrrelevantPapers.keySet() );
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
   * Tear down the test harness.
   * @throws IOException
   * @throws WriteException
   */
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
    
    for ( I pmid : rankOutput.keySet() ) {
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
