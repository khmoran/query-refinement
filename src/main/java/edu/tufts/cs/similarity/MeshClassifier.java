package edu.tufts.cs.similarity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.util.MathUtil;

public class MeshClassifier {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      MeshClassifier.class );
  protected static final Map<String, Double> infoMap = new HashMap<>();

  /**
   * Default constructor.
   */
  public MeshClassifier() {
    
  }

  /**
   * Constructor that initializes the classifier.
   * @param l1
   * @param l2
   */
  public MeshClassifier( Set<Citation> l1, Set<Citation> l2 ) {
    update( l1, l2 );
  }

  /**
   * Classify the Citation based on its MeSH terms.
   * @param c
   * @return
   */
  public double classify( Citation c ) {
    double total = 0.0;
    for ( String term : c.getMeshTerms() ) {
      term += infoMap.get( term );
    }
    
    return total;
  }

  /**
   * Calculate the info gain of the MeSH terms from the L1 and L2 sets.
   * @param l1
   * @param l2
   * @return
   */
  public void update( Set<Citation> l1, Set<Citation> l2 ) {
    // Get the MeSH terms for the relevant papers
    Set<String> l1Mesh = new HashSet<>();
    for ( Citation c : l1 ) {
      l1Mesh.addAll( c.getMeshTerms() );
    }
    LOG.trace( "# MeSH terms from L1 papers: " + l1Mesh.size() );

    // Get the MeSH terms for the irrelevant papers
    Set<String> l2Mesh = new HashSet<>();
    for ( Citation c : l2 ) {
      l2Mesh.addAll( c.getMeshTerms() );
    }

    double initialEntropy = MathUtil.calculateShannonEntropy( l2.size(),
        l1.size() );

    Set<String> allMesh = new HashSet<>();
    allMesh.addAll( l1Mesh );
    allMesh.addAll( l2Mesh );

    for ( String mesh : allMesh ) {
      int numL1 = 0;
      int numL2 = 0;
      for ( Citation c : l1 ) {
        if ( c.getMeshTerms().contains( mesh ) ) {
          numL1++;
        }
      }
      for ( Citation c : l2 ) {
        if ( c.getMeshTerms().contains( mesh ) ) {
          numL2++;
        }
      }

      // calculate the info gain
      double entropyPresent = MathUtil.calculateShannonEntropy(
          numL2, numL1 );
      double entropyNotPresent = MathUtil.calculateShannonEntropy(
          l2.size() - numL2, l1.size() - numL1 );

      double infoGain = MathUtil.calculateInfoGain(
          initialEntropy, entropyPresent, entropyNotPresent );
      if ( infoGain < 0 ) infoGain = 0;
      
      // account for positivity/negativitiy
      int pos = ( numL2 > numL1 ) ? 1 : -1;
      infoGain = infoGain*pos;
      
      LOG.trace( mesh + " \t#l1: " + numL1 + "; #l2: " + numL2 + "\tig: " +
        infoGain + "\n" );
      
      infoMap.put( mesh, infoGain );
    }
  }
}
