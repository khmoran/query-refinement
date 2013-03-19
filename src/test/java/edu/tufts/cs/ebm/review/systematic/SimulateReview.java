package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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

/**
 * Test the MeshWalker class.
 */
public class SimulateReview extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReview.class );
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 5;
  /** The threshold for information gain being "useful". */
  protected static final double INFO_GAIN_THRESHOLD = 0.4;
  /** The file containing the recall statistics. */
  protected String statsFile;
  /** The file containing the rankings of the papers. */
  protected String paperRankFile;
  /** The prior for the number of papers returned by the query. */
  //protected int n;
  /** The csv printer. */
  protected SXSSFWorkbook workbook;
  /** The first sheet of the workbook. */
  protected Sheet sheet;
  /** The second sheet of the workbook. */
  protected Sheet sheet2;
  /** The map from citation to row in the workbook. */
  protected Map<PubmedId, Integer> rankMap = new HashMap<>();
  /** The maximum row in the workbook. */
  protected int maxRow = 0;
  /** The maximum column in the workbook. */
  protected int maxCol = 4;
  /** The iteration. */
  protected int iteration = 0;
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
    double z = 0;
    for ( int i = 1; i <= cosineMap.values().size(); i++ ) {
      z += 1/(double) ( i );
      //z += 1/(double) ( i * i );
    }

    Row r = sheet.getRow( 0 );
    if ( r == null ) r = sheet.createRow( 0 );
    Cell cell = r.createCell( maxCol );
    cell.setCellValue( iteration );

    int rank = 1;
    for ( Double sim : cosineMap.keySet().descendingSet() ) {
      for ( Citation c : cosineMap.get( sim ) ) {
        PubmedId pmid = c.getPmid();
        int row;
        if ( rankMap.keySet().contains( pmid ) ) {
          row = rankMap.get( pmid );
          r = sheet.getRow( row );
        } else {
          row = ++maxRow;
          rankMap.put( pmid, row );  // give it a row

          r = sheet.getRow( row );
          if ( r == null ) r = sheet.createRow( row );
          cell = r.createCell( 0 );
          cell.setCellValue( pmid.toString() );

          boolean level1 = false, level2 = false;
          if ( activeReview.getRelevantLevel1().contains( pmid ) ) {
            level1 = true;

            if ( activeReview.getRelevantLevel2().contains( pmid ) ) {
              level2 = true;
            }
          }

          cell = r.createCell( 1 );
          cell.setCellValue( level1 );
          cell = r.createCell( 2 );
          cell.setCellValue( level2 );
        }

        cell = r.createCell( maxCol );
        cell.setCellValue( rank );

        Row r2 = sheet2.getRow( row );
        if ( r2 == null ) r2 = sheet2.createRow( row );
        Cell cell2 = r2.createCell( maxCol );
        double prob = ( (double) 1/ (double) rank ) / (double) z;
        //double prob = ( (double) 1/ (double) rank*rank ) / (double) z;
        cell2.setCellValue( prob );

        rank++;
      }
    }

    Set<Citation> allObserved = new HashSet<>();
    allObserved.addAll( expertRelevantPapers );
    allObserved.addAll( expertIrrelevantPapers );
    for ( Citation c : allObserved ) {
      int row = rankMap.get( c.getPmid() );
      r = sheet.getRow( row );

      if ( r.getCell( 3 ) == null ) {
        cell = r.createCell( 3 );
        cell.setCellValue( iteration );
      }
    }

    iteration++;
    maxCol++;
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
   * Perform the search and rank the results.
   * @param searcher
   * @return
   * @throws InterruptedException
   */
  protected void search( ParallelPubmedSearcher searcher ) {
    try {
      LOG.info( "Performing search..." );
      Thread t = new Thread( searcher );
      t.start();
      t.join();
    } catch ( InterruptedException e ) {
      LOG.error( "Could not complete query on PubMed. Exiting.", e );
      System.exit( 1 );
    }
  }

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    if ( this.getClass().getSimpleName().equals(
        "SimulateReviewProtonBeam" ) ) {
      this.activeReview = protonBeamReview;
    } else if ( this.getClass().getSimpleName().equals(
        "SimulateReviewClopidogrel" ) ) {
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

    workbook = new SXSSFWorkbook( 29000 );
    sheet = workbook.createSheet( "ranks" );
    sheet2 = workbook.createSheet( "probabilities" );

    Row r = sheet.createRow( 0 );
    Cell pmidHeader = r.createCell( 0 );
    pmidHeader.setCellValue( "pmid" );
    Cell level1Header = r.createCell( 1 );
    level1Header.setCellValue( "level 1 inclusion" );
    Cell level2Header = r.createCell( 2 );
    level2Header.setCellValue( "level 2 inclusion" );
    Cell itObservedHeader = r.createCell( 3 );
    itObservedHeader.setCellValue( "iteration observed" );
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
      Map<String, InfoMeasure> im = evaluateQuery( searcher, cosineMap,
        expertRelevantPapers, expertIrrelevantPapers );

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
  @AfterSuite
  public void tearDown() throws IOException {
    FileOutputStream out = new FileOutputStream( paperRankFile );

    workbook.write( out );
    out.close();

    // dispose of temp files
    workbook.dispose();
  }
}
