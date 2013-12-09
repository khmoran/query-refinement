package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.text.BagOfWords;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation.
 */
public abstract class OnlineSimulatorBow extends
    OnlineSimulator<PubmedId, FeatureVector<Integer>> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( OnlineSimulatorBow.class );
  /** The Bag of Words. */
  protected BagOfWords<Integer> bow;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBow( String review ) throws Exception {
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
      Set<Citation> citations ) {
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

    return fvs;
  }
}
