package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.review.systematic.simulation.Simulator;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.reader.Reader;
import edu.tufts.cs.ml.reader.SvmLightReader;

// TODO abstract this class and then implement a separate cosine similarity/BoW extension
/**
 * An offline simulation of a systematic review.
 */
public class OfflineSimulator extends Simulator {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OfflineSimulator.class );
  /** The portion of documents to observe. */
  protected static final double PERCENT_TO_OBSERVE = .6;
  /** The number of iterations to run. */
  protected static final int NUM_ITERATIONS = 10;
  /** The default header. */
  protected static final String DEFAULT_HEADER = "dataset\trun\ttotal labels\t" +
      "new labels\trecall\t% observed relevant\t% expected relevant\tobserved";
  /** The file containing the recall statistics. */
  protected String statsFile;
  /** The file containing the rankings of the papers. */
  protected String paperRankFile;
  /** The file containing the probabilities of the papers. */
  protected String paperProbFile;
  /** The rankings output. */
  protected Map<String, String> rankOutput = new HashMap<>();
  /** The observations output. */
  protected Map<String, String> observOutput = new HashMap<>();
  /** The probabilities output. */
  protected Map<String, String> probOutput = new HashMap<>();
  /** The combined result file. */
  protected String combinedResultsFile;
  /** The name of the dataset. */
  protected String datasetName;
  /** The datasetFile. */
  protected String datasetFile;
  
  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  public OfflineSimulator( String file, String name ) throws Exception {
    this.datasetFile = file;
    this.datasetName = name;
    this.statsFile = "stats-" + name + ".csv";
    this.paperRankFile = "ranks-" + name + "copd.csv";
    this.paperProbFile = "probs-" + name + "copd.csv";
    this.combinedResultsFile = "combined-results-" + name + ".tdf";

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    // initialize the cache
    try {
      defaultCache = JCS.getInstance( DEFAULT_CACHE_NAME );
    } catch ( CacheException e ) {
      LOG.error( "Error intitializing prefetching cache.", e );
      e.printStackTrace();
    }
  }

  /**
   * Evaluate the query.
   * @param searcher
   * @return
   */
  protected Map<String, InfoMeasure> evaluateQuery(
      TrainRelation<Integer> data, TreeMultimap<Double, String> cosineMap,
      Set<String> observedRelevant, Set<String> observedIrrelevant ) {

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
      for ( String feat : cosineMap.get( sim ) ) {
        if ( data.get( feat ).getLabel() == 1 ) {
          truePosTotal++;
          if ( !observedRelevant.contains( feat ) ) {
            if ( i < data.countClass( 1 ) ) {
              truePosL1++;

              if ( //i < activeReview.getRelevantLevel2().size() &&
                  data.get( feat ).getLabel() == 2 ) { // if it's in L2
                truePosL2++;
              }
            }
          } else {
            if ( data.get( feat ).getLabel() == 2 ) { // if it's in L2
              l2ExpertRel++;
            }
          }
        }
        iMap.put( i, truePosTotal );
        if ( !observedRelevant.contains( feat ) &&   // want to pretend
            !observedIrrelevant.contains( feat ) ) { // these aren't in
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
    InfoMeasure l1Info = new InfoMeasure( observedRelevant.size(), // + truePosL1,
        data.countClass( 1 ) );
    InfoMeasure l2Info = new InfoMeasure( truePosL2 + l2ExpertRel,
        data.countClass( 2 ) );
    InfoMeasure totalInfo = new InfoMeasure( truePosTotal,
        data.countClass( 1 ) );
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
  protected Set<String> getPaperProposals( TrainRelation<Integer> searcher,
      TreeMultimap<Double, String> cosineMap,
      Set<String> observedRelevant, Set<String> observedIrrelevant ) {
    Set<String> results = new HashSet<>();

    List<String> citList = new ArrayList<>();
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( String feat : cosineMap.get( sim ) ) {
        // WITH REPLACEMENT:
        citList.add( feat );
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

  @SuppressWarnings( "unchecked" )
  public void performIteration( BufferedWriter results, int iteration )
    throws IOException {
    // prepare the CSV output
    FileWriter fw = new FileWriter( statsFile );
    BufferedWriter out = new BufferedWriter( fw );
   // header row
    out.write(
      "i,papers proposed,papers added,L1 cost,L1 recall,L2cost,L2recall" );
    out.newLine();
    out.flush();

    // load up the data from the feature vectors
    Reader<String> r = new SvmLightReader<String>( );
    TrainRelation<Integer> data = (TrainRelation<Integer>) r.read(
        new File( datasetFile ) );
    data.normalize();
    
    LOG.info( "Total data set size: " + data.size() );
    LOG.info( "Relevant data set size: " + data.countClass( 1 ) );

    // TODO this needs to contain seed set
    TrainRelation<Integer> compareTo = new TrainRelation<>(
        "compareTo", new Metadata() );
    Set<String> observedRelevant = new HashSet<>();
    Set<String> observedIrrelevant = new HashSet<>();

    // gather initial statistics on the results
    TreeMultimap<Double, String> cosineMap = rank( data, compareTo,
        observedRelevant, observedIrrelevant );
    evaluateQuery( data, cosineMap, observedRelevant, observedIrrelevant );
    
    int numPapersToObserve = (int) Math.ceil(
        data.size() * PERCENT_TO_OBSERVE );
    
    LOG.info( "Observing at least " + PERCENT_TO_OBSERVE +
        " of available papers ("+ numPapersToObserve + ")" );

    int i = 0;
    while ( ( observedRelevant.size() + observedIrrelevant.size() )
        < numPapersToObserve ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<String> paperProposals = getPaperProposals( data, cosineMap,
          observedRelevant, observedIrrelevant );

      int numRelevant = observedRelevant.size();
      int numNew = 0;
      double observedRel = 0.0;
      double expectedRel = (double) data.countClass( 1 ) / (double) data.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        break;
      } else {
        Set<String> newRelevant = proposePapers( data, paperProposals,
          observedRelevant, observedIrrelevant );
        numNew = newRelevant.size();

        observedRel = (double) newRelevant.size() /
            (double) paperProposals.size();
        expectedRel = ( data.countClass( 1 ) -
            observedRelevant.size() ) / ( (double) data.size() -
                observedRelevant.size() - observedIrrelevant.size() );
        LOG.info( "% observed relevant: " + observedRel );
        LOG.info( "% expected relevant: " + expectedRel );

        // make sure the sim calculations are done on the latest set
        if ( observedRelevant.size() > numRelevant ) {
          updateSimilarities( data, compareTo, newRelevant );
        }
        LOG.debug( "\t# relevant: " + observedRelevant.size() );
        LOG.debug( "\t# irrelevant: " + observedIrrelevant.size() );

        // if new papers are proposed, update the ranking
        if ( observedRelevant.size() > numRelevant ) {
          cosineMap = rank( data, compareTo, observedRelevant, paperProposals );
        } else { // but either way record the ranks
          recordRank( cosineMap, paperProposals );
        }
      }

      Map<String, InfoMeasure> im = evaluateQuery( data, cosineMap,
        observedRelevant, observedIrrelevant );

      if ( im != null ) {
        // write out the current stats
        int observed = observedRelevant.size() + observedIrrelevant.size();
        double costL1 = observed; //+ data.countClass( 1 ) -
            //im.get( "L1" ).getTruePositives();
        double costL2 = observed; // + data.countClass( 1 ) -
            //im.get( "L2" ).getTruePositives();
        out.write( i + "," + observed + "," +
          observedRelevant.size() +
          "," + costL1 + "," + im.get( "L1" ).getRecall() +
          "," + costL2 + "," + im.get( "L2" ).getRecall() );
      }

      out.newLine();
      out.flush();      

      results.append( datasetName + "\t" + iteration + "\t" +
        observedRelevant.size() + "\t" + numNew + "\t" +
        im.get( "L1" ).getRecall() + "\t" + observedRel + "\t" + expectedRel
        + "\t" + paperProposals.toString() );
      results.newLine();
      results.flush();
    }

    out.close();
    fw.close();
  }

  /**
   * Propose the papers to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<String> proposePapers( TrainRelation<Integer> data,
      Set<String> proposals, Set<String> observedRelevant,
      Set<String> observedIrrelevant ) {
    LOG.info( "Proposing papers..." );
    Set<String> newRelevant = new HashSet<>();
    for ( String feat : proposals ) {
      if ( data.get( feat ).getLabel() == 1 ) {
        // LOG.debug( "\t" + feat + " is relevant" );
        observedRelevant.add( feat );
        newRelevant.add( feat );
      } else {
        // LOG.debug( "\t" + feat + " is irrelevant" );
        observedIrrelevant.add( feat );
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
  @SuppressWarnings( "unchecked" )
  protected TreeMultimap<Double, String> rank( TrainRelation<Integer> data,
      TrainRelation<Integer> compareTo, Set<String> observedRelevant,
      Set<String> proposals ) {
    TreeMultimap<Double, String> cosineMap = TreeMultimap.create();
    for ( LabeledFeatureVector<Integer> doc : data ) {
      Map<String, Double> simMap = null;
      if ( defaultCache != null ) {
        simMap = (Map<String, Double>) defaultCache.get( doc.getId() );
        try {
          defaultCache.remove( doc.getId() );
        } catch ( CacheException e ) {
          LOG.error( "Unable to remove from cache: " + doc.getId(), e );
        }
      }
      if ( simMap == null ) {
        simMap = new HashMap<>();
      }

      double totalSim = 0.0;
      for ( LabeledFeatureVector<Integer> rel : compareTo ) {
        Double sim = simMap.get( rel.getId() );
        if ( sim == null ) {
          sim = ( doc.dot( rel ) / doc.magnitude() * rel.magnitude() );
          simMap.put( rel.getId(), sim );
        }
        totalSim += sim;
      }

      double avgSim = totalSim/observedRelevant.size();
      cosineMap.put( avgSim, doc.getId() );

      try {
        if ( defaultCache != null ) defaultCache.put( doc.getId(), simMap );
      } catch ( CacheException e ) {
        LOG.error( "Unable to cache: " + e );
      }
    }

    return cosineMap;
  }

  /**
   * Record the current ranking.
   * @param cosineMap
   */
  protected void recordRank( TreeMultimap<Double, String> cosineMap,
      Set<String> proposals ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( cosineMap.values().size() );
    }

    int rank = 1;
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( String feat : cosineMap.get( sim ) ) {
       
        String rankStr = rankOutput.get( feat );
        if ( rankStr == null ) {
          rankStr = "";
        } else {
          rankStr += ",";
        }
        rankStr += String.valueOf( rank );
        rankOutput.put( feat, rankStr );

        double prob = (double) 1/ (double) rank / z;
        prob = MathUtil.round( prob, 7 );

        String probStr = probOutput.get( feat );
        if ( probStr == null ) {
          probStr = "";
        } else {
          probStr += ",";
        }
        probStr += String.valueOf( prob );
        probOutput.put( feat, probStr );

        rank++;
      }
    }

    for ( String feat : proposals ) {
      String observStr = observOutput.get( feat );
      if ( observStr == null ) {
        observStr = "";
      } else {
        observStr += ",";
      }
      observStr += String.valueOf( iteration );
      observOutput.put( feat, observStr );
    }

    iteration++;
  }
  
  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Override
  public void simulateReview() throws InterruptedException, IOException {
    FileWriter fw = new FileWriter( combinedResultsFile );
    BufferedWriter out = new BufferedWriter( fw );
   // header row
    out.write( DEFAULT_HEADER );
    out.newLine();
    out.flush();

    for ( int i = 0; i < NUM_ITERATIONS; i++ ) {
      performIteration( out, i );
    }
  }

  /**
   * Update the set to compare to during cosine similarity.
   * @param newRelevant
   */
  protected void updateSimilarities( TrainRelation<Integer> data,
      TrainRelation<Integer> compareTo, Collection<String> newRelevant ) {
    for ( String featId : newRelevant ) {
      compareTo.add( data.get( featId ) );
    }
  }
}
