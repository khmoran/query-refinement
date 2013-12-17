package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.io.File;
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
 * An online simulation of a systematic review using a Bag of Words
 * representation.
 */
public abstract class OnlineSimulatorMesh extends
    OnlineSimulator<PubmedId, FeatureVector<Integer>> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorMesh.class );

  /** The Bag of Words. */
  protected BagOfWords<Integer> bow;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorMesh( String review ) throws Exception {
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
    bow = new BagOfWords<Integer>( new File(
        "src/main/resources/stoplists/en.txt" ) );

    // create the features
    List<String> texts = new ArrayList<String>();
    for ( Citation c : citations ) {
      String mesh = c.getMeshStr().replaceAll( ",", " " );
      texts.add( mesh );
    }
    bow.createFeatures( texts );

    for ( Citation c : activeReview.getSeedCitations() ) {
      // seed citations are in the positive class
      bow.train( c.getPmid().toString(), c.getTitle() + " " + c.getAbstr(), 1 );
    }

    // create the feature vectors
    Map<PubmedId, FeatureVector<Integer>> fvs = new HashMap<>();
    for ( Citation c : citations ) {
      String mesh = c.getMeshStr().replaceAll( ",", " " );
      fvs.put( c.getPmid(),
          bow.createUnlabeledFV( c.getPmid().toString(), mesh ) );
    }

    return fvs;
  }
}
