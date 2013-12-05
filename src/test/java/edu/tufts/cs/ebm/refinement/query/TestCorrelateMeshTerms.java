package edu.tufts.cs.ebm.refinement.query;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.AbstractTest;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.ebm.util.Util;

public class TestCorrelateMeshTerms extends AbstractTest {
  /** The active review. */
  protected SystematicReview activeReview;
  /** The cache name. */
  protected static final String DEFAULT_CACHE_NAME = "default";
  /** The data cache. */
  protected static JCS defaultCache;
  /** The stop words. */
  protected List<String> stopWords;

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    this.activeReview = clopidogrelReview;

    Runtime rt = Runtime.getRuntime();
    LOG.info( "Max Memory: " + rt.maxMemory() / MB );

    LOG.info( "# seeds:\t " + activeReview.getSeeds().size() );
    LOG.info( "# relevant L1:\t " + activeReview.getRelevantLevel1().size() );
    LOG.info( "# relevant L2:\t " + activeReview.getRelevantLevel2().size() );
    LOG.info( "# blacklisted:\t " + activeReview.getBlacklist().size() );

    // load up the relevant papers
    for ( PubmedId pmid : activeReview.getRelevantLevel2() ) {
      MainController.EM.find( PubmedId.class, pmid.getValue() );
    }

    // initialize the cache
    try {
      defaultCache = JCS.getInstance( DEFAULT_CACHE_NAME );
    } catch ( CacheException e ) {
      LOG.error( "Error intitializing prefetching cache.", e );
      e.printStackTrace();
    }
    
    this.stopWords = loadStopWords();
  }

  /**
   * Load the stop words.
   * @return
   * @throws IOException
   */
  protected List<String> loadStopWords() throws IOException {
    File f = new File( "src/main/resources/stopwords.txt" );

    FileReader fr = new FileReader( f );
    BufferedReader br = new BufferedReader( fr );

    List<String> stopWords = new ArrayList<>();
    String line;
    while ( ( line = br.readLine() ) != null ) {
      String[] split = line.split( "," );
      for ( String s : split ) {
        String normalized = Util.normalize( s );
        
        if ( !normalized.isEmpty() ) {
          stopWords.add( normalized );
        }
      }
    }
    br.close();
    
    System.out.println( stopWords );
    
    return stopWords;
  }
  
  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  public void simulateCorrelateMeshTerms()
    throws InterruptedException, IOException {
    StringBuffer popQuery = new StringBuffer( activeReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( activeReview.getQueryIC() );

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );

    // run the initial query
    String query = "(" + popQuery + ") AND (" + icQuery + ")".toLowerCase();
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        query, activeReview );
    search( searcher );
    
    Map<String, Map<String, Integer>> meshToWordMap = new HashMap<>();
    Map<String, Integer> wordCounts = new HashMap<String, Integer>();
    Map<String, Integer> meshCounts = new HashMap<>();
    for ( Citation c : searcher.getCitations() ) {
      for ( String mesh : c.getMeshTerms() ) {
        // get a tally of how often this MeSH term is seen
        Integer meshCount = meshCounts.get( mesh );
        if ( meshCount == null ) meshCount = 0;
        meshCounts.put( mesh, ++meshCount );

        // tally how often words are seen in articles tagged with this MeSH
        Map<String, Integer> wordCount = meshToWordMap.get( mesh );
        if ( wordCount == null ) {
          wordCount = new HashMap<String, Integer>();
          meshToWordMap.put( mesh, wordCount );
        }
        
        for ( String word : Util.tokenize( c.getAbstr() ) ) {
          word = word.toLowerCase(); // to normalize     
          if ( stopWords.contains( word ) || query.contains( word ) ) continue;

          Integer count = wordCount.get( word );
          if ( count == null ) count = 0;
          wordCount.put( word, ++count );
          
          count = wordCounts.get( word );
          if ( count == null ) count = 0;
          wordCounts.put( word, ++count );
        }
      }
    }

    // get rid of domain-specific stop words
    LOG.info( "Ranking words by entropy..." );
    Map<String, Double> entropyMap = new HashMap<>();
    for ( String word : wordCounts.keySet() ) {
      // get frequencies
      List<Integer> wordFreqs = new ArrayList<Integer>();
      for ( Map<String, Integer> wordMap : meshToWordMap.values() ) {
        Integer count = wordMap.get( word );
        if ( count != null ) wordFreqs.add( count );
        else wordFreqs.add( 0 );
      }
      // we have all the counts for this word... now get shannon entropy
      double entropy = MathUtil.calculateShannonEntropy( wordFreqs );
      entropyMap.put( word, entropy );
    }
    List<String> orderedEntropy = Ordering.natural().onResultOf(
        Functions.forMap( entropyMap ) ).immutableSortedCopy( entropyMap.keySet() ).reverse();

    List<String> sortedMesh = Ordering.natural().onResultOf(
        Functions.forMap( meshCounts ) ).immutableSortedCopy( meshCounts.keySet() ).reverse();
    StringBuilder output = new StringBuilder();
    for ( String mesh : sortedMesh ) {
      Map<String, Integer> freqMap = meshToWordMap.get( mesh );
      List<String> sortedKeys = Ordering.natural().onResultOf(
          Functions.forMap( freqMap) ).immutableSortedCopy( freqMap.keySet() ).reverse();
      output.append( mesh + " (" + meshCounts.get( mesh ) + "):\t" );
      int i = 0;
      for ( String word : sortedKeys ) {
        // don't look at things that are too infrequent or too frequent
        if ( word.length() < 4 || wordCounts.get( word ) == 1 ||
            orderedEntropy.indexOf( word ) < ( orderedEntropy.size()*.8 ) ) {
          continue;
        } else if ( i > 10 ) {
          break;
        } else {
          output.append( word + " (" + meshToWordMap.get( mesh ).get( word ) + ")");
          if ( i < 10 ) output.append( ", " );
          i++;
        }
      }
      output.append( "\n" );
    }

    LOG.info( "# unique MeSH terms: " + meshCounts.size() );
    File results = new File( "mesh-to-word.txt" );
    FileWriter fw = new FileWriter( results );
    BufferedWriter out = new BufferedWriter( fw );
    out.write( output.toString() );
    out.close();
    fw.close();
  }

}
