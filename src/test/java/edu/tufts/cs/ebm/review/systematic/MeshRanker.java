package edu.tufts.cs.ebm.review.systematic;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.PubmedLocator;
import edu.tufts.cs.ebm.refinement.query.PubmedSearcher;
import edu.tufts.cs.ebm.util.MathUtil;

/**
 * Test the MeshWalker class.
 */
public class MeshRanker extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( MeshRanker.class );
  /** The population searcher. */
  protected PubmedSearcher defaultSearcher;
  /** The out file. */
  protected static final String REL_OUT_FILE = "relevanceCount.out";
  /** The out file. */
  protected static final String IG_OUT_FILE = "meshRanking.out";

  /**
   * Set up the test suite.
   * 
   * @throws IOException
   */
  @BeforeSuite
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   * 
   * @throws RemoteException
   * @throws InterruptedException
   */
  @Test
  public void simulateClopidogrelReview() throws RemoteException,
      InterruptedException {
    PubmedLocator locator = new PubmedLocator();

    // Get the MeSH terms for the relevant papers
    Set<Citation> relevant = new HashSet<>(
        locator.getCitations( clopidogrelReview.getRelevantLevel1() ) );

    Set<String> relevantMesh = new HashSet<>();
    for ( Citation c : relevant ) {
      relevantMesh.addAll( c.getMeshTerms() );
    }
    LOG.info( "# MeSH terms from relevant papers: " + relevantMesh.size() );

    // Get the MeSH terms for the irrelevant papers
    Set<Citation> irrelevant = new HashSet<>(
        locator.getCitations( clopidogrelReview.getIrrelevantP() ) );

    Set<String> irrelevantMesh = new HashSet<>();
    for ( Citation c : irrelevant ) {
      irrelevantMesh.addAll( c.getMeshTerms() );
    }
    LOG.info( "# MeSH terms from irrelevant papers: " + irrelevantMesh.size() );

    double initialEntropy = MathUtil.calculateShannonEntropy( relevant.size(),
        irrelevant.size() );
    LOG.info( "Initial entropy: " + initialEntropy );

    Set<String> allMesh = new HashSet<>();
    allMesh.addAll( relevantMesh );
    allMesh.addAll( irrelevantMesh );

    LOG.info( "# total terms: " + allMesh.size() );

    StringBuilder posStr = new StringBuilder();
    TreeMultimap<Double, String> infoGains = TreeMultimap.create();
    Map<String, Integer> positivityMap = new HashMap<>();
    for ( String mesh : allMesh ) {
      int numRelevant = 0;
      int numIrrelevant = 0;
      for ( Citation c : relevant ) {
        if ( c.getMeshTerms().contains( mesh ) ) {
          numRelevant++;
        }
      }
      for ( Citation c : irrelevant ) {
        if ( c.getMeshTerms().contains( mesh ) ) {
          numIrrelevant++;
        }
      }
      positivityMap.put( mesh, numRelevant - numIrrelevant );

      posStr.append( mesh + " \t#relevant: " + numRelevant + "; #irrelevant:"
          + numIrrelevant + "\n" );

      // calculate the info gain
      double entropyPresent = MathUtil.calculateShannonEntropy( numRelevant,
          numIrrelevant );
      double entropyNotPresent = MathUtil.calculateShannonEntropy(
          relevant.size() - numRelevant, irrelevant.size() - numIrrelevant );

      posStr.append( " \tentropyPresent: " + entropyPresent
          + "; entropyNotPresent:" + entropyNotPresent + "\n" );

      double infoGain = MathUtil.calculateInfoGain( initialEntropy,
          entropyPresent, entropyNotPresent );
      infoGains.put( infoGain, mesh );
    }

    // TreeMultimap<Double, String> normalized = MathUtil.normalize( infoGains
    // );

    StringBuilder igStr = new StringBuilder();
    for ( Double infoGain : infoGains.keySet().descendingSet() ) {
      Collection<String> terms = infoGains.get( infoGain );

      Double rounded = MathUtil.round( infoGain, 6 );
      for ( String term : terms ) {
        boolean positive = positivityMap.get( term ) > 0;
        String posChar = positive ? "+" : "-";
        igStr.append( rounded + " \t" + " (" + posChar + "): " + term + "\n" );
      }
    }

    /*
     * Write the output.
     */
    try {
      FileOutputStream fos = new FileOutputStream( IG_OUT_FILE );
      OutputStreamWriter out = new OutputStreamWriter( fos, "UTF-8" );
      out.write( igStr.toString() );
      out.close();
      fos.close();

      fos = new FileOutputStream( REL_OUT_FILE );
      out = new OutputStreamWriter( fos, "UTF-8" );
      out.write( posStr.toString() );
      out.close();
      fos.close();
    } catch ( IOException e ) {
      LOG.error( "Could not write output to file.", e );
    }
  }
}
