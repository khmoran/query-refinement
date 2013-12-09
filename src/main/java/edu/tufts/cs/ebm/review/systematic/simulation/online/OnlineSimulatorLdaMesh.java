package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Multimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ml.DoubleFeature;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.text.BagOfWords;
import edu.tufts.cs.ml.topics.lda.BasicLDA;
import edu.tufts.cs.ml.topics.lda.LDA;

/**
 * An online simulation of a systematic review using an LDA representation.
 */
public abstract class OnlineSimulatorLdaMesh extends
    OnlineSimulator<PubmedId, FeatureVector<Integer>> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( OnlineSimulatorLdaMesh.class );
  /** The expected number of topics. */
  public static final int NUM_TOPICS = 20;
  /** The alpha sum prior. */
  public static final double ALPHA_SUM_PRIOR = 10;
  /** The beta prior. */
  public static final double BETA_PRIOR = 0.01;
  /** The number of LDA iterations. */
  public static final int NUM_LDA_IT = 2000;
  /** The LDA model. */
  protected LDA lda;

  /**
   * Default constructor.
   * 
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorLdaMesh( String review ) throws Exception {
    super( review );
  }

  @Override
  protected Map<PubmedId, FeatureVector<Integer>> createFeatureVectors(
      Set<Citation> citations ) {
    StringBuilder sb = new StringBuilder();
    BagOfWords<Integer> meshBow = new BagOfWords<Integer>( new File(
        "src/main/resources/stoplists/en.txt" ) );

    List<String> meshStr = new ArrayList<String>();
    for ( Citation c : citations ) {
      String mesh = c.getMeshStr().replaceAll( ",", " " );
      meshStr.add( mesh );
      sb.append( c.getPmid() + "\tX\t\"" + c.getTitle() + " " + c.getAbstr()
          + " " + mesh + "\"\n" );
    }

    meshBow.createFeatures( meshStr );

    this.lda = new BasicLDA( NUM_TOPICS, ALPHA_SUM_PRIOR, BETA_PRIOR );
    try {
      lda.train( sb.toString(), NUM_LDA_IT );
    } catch ( IOException e ) {
      LOG.error( e );
    }

    // create the feature vectors
    Map<PubmedId, FeatureVector<Integer>> fvs = new HashMap<>();
    for ( Citation c : citations ) {
      String mesh = c.getMeshStr().replaceAll( ",", " " );
      fvs.put( c.getPmid(), createUnlabeledVector( c, meshBow, mesh ) );
    }

    return fvs;
  }

  /**
   * Create a FeatureVector from the LDA data.
   * 
   * @param c
   * @param relevant
   * @return
   */
  protected UnlabeledFeatureVector<Integer> createUnlabeledVector( Citation c,
      BagOfWords<Integer> bow, String mesh ) {
    // start with a BoW representation for the MeSH terms
    UnlabeledFeatureVector<Integer> fv = bow.createUnlabeledFV( c.getPmid()
        .toString(), mesh );
    // then appen the LDA topics as features
    Multimap<Double, Integer> topicDist = lda
        .getTopics( c.getPmid().toString() );
    for ( double pct : topicDist.keySet() ) {
      for ( int topic : topicDist.get( pct ) ) {
        String str = String.valueOf( topic );
        fv.put( str, new DoubleFeature( str, pct ) );
      }
    }

    return fv;
  }
}
