package edu.tufts.cs.ebm.review.systematic.simulation.offline;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.text.BagOfWords;

/**
 * An offline simulation of a systematic review.
 */
public abstract class OfflineSimulatorBow extends
    OfflineSimulator<PubmedId, FeatureVector<Integer>> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OfflineSimulatorBow.class );
  /** The Bag of Words. */
  protected BagOfWords<Integer> bow;

  /**
   * Default constructor.
   *
   * @param review
   * @throws Exception
   */
  public OfflineSimulatorBow( String review ) throws Exception {
    super( review );
  }

  /**
   * Turn the Citations into FeatureVectors.
   * 
   * @param citations
   * @return
   */
  @Override
  protected Map<PubmedId, FeatureVector<Integer>> createFeatureVectors(
      Collection<Citation> citations ) {
    // initialize the bag of words
    final int minOccurs = 5;
    final int minLength = 3;
    bow = new BagOfWords<Integer>( new File(
        "src/main/resources/stoplists/en.txt" ), true, false, minOccurs,
        minLength );

    // create the features
    List<String> texts = new ArrayList<String>();
    for ( Citation c : citations ) {
      texts.add( c.getTitle() + " " + c.getAbstr() );
    }
    bow.createFeatures( texts );

    for ( Citation c : activeReview.getSeedCitations() ) {
      // seed citations are in the positive class
      bow.train( c.getPmid().toString(), c.getTitle() + " " + c.getAbstr(), 1 );
    }

    // create the feature vectors
    Map<PubmedId, FeatureVector<Integer>> fvs = new HashMap<>();
    for ( Citation c : citations ) {
      fvs.put(
          c.getPmid(),
          bow.createUnlabeledFV( c.getPmid().toString(),
              c.getTitle() + " " + c.getAbstr() ) );
    }

    File f = new File( "src/test/resources/" + this.dataset
        + "-labeled-terms.csv" );
    try {
      loadPseudoDocuments( f );
    } catch ( IOException e ) {
      LOG.error(
          "Could not load labeled psuedodocument terms from " + f.toString(), e );
    }

    return fvs;
  }

  /**
   * Load up the pseudo-documents (labeled terms).
   * 
   * @param f
   * @throws IOException
   */
  protected void loadPseudoDocuments( File f ) throws IOException {
    Path path = Paths.get( f.getPath() );
    List<String> terms = Files.readAllLines( path, Charset.defaultCharset() );

    LOG.info( "Loaded " + terms.size()
        + " labeled terms for psueodocuments." );

    StringBuilder posPseudoDoc = new StringBuilder();
    StringBuilder negPseudoDoc = new StringBuilder();
    for ( String term : terms ) {
      if ( term.contains( "-" ) ) {
        negPseudoDoc.append( " " + term.substring( 0, term.length()-2 ) );
      } else if ( term.contains( "+" ) ) {
        posPseudoDoc.append( " " + term.substring( 0, term.length()-2 ) );
      } else {
        posPseudoDoc.append( " " + term );
      }
    }

    for ( int i = 0; i < numReplications; i++ ) {
      FeatureVector<Integer> pseudoNeg = bow.createUnlabeledFV(
          PSEUDO_PREFIX + "neg_" + i, negPseudoDoc.toString() );
      FeatureVector<Integer> pseudoPos = bow.createUnlabeledFV(
          PSEUDO_PREFIX + "pos_" + i, posPseudoDoc.toString() );
      labeledTerms.put( pseudoPos, PSEUDO_POS );
      labeledTerms.put( pseudoNeg, PSEUDO_NEG );
    }
  }

}
