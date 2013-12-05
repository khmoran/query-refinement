package edu.tufts.cs.ebm.review.systematic.simulation.online;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.util.Util;
import edu.tufts.cs.ml.FeatureVector;
import edu.tufts.cs.ml.TestRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;

/**
 * An online simulation of a systematic review using a Bag of Words
 * representation and an SVM classifier.
 */
public class OnlineSimulatorBowRankSvmLabeledTerms extends OnlineSimulatorBowRankSvmTwoTier {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      OnlineSimulatorBowRankSvmLabeledTerms.class );
  protected static final String PSEUDO_PREFIX = "psuedo_";
  /** The positive class label for L2. */
  protected static final int POS_L2 = 1;
  /** The positive class label for L1. */
  protected static final int POS_L1 = 2;
  /** The negative class label. */
  protected static final int NEG = 3;
  /** The pseduo document positive class label. */
  protected static final int PSEUDO_POS = 0;
  /** The pseduo document negative class label. */
  protected static final int PSEUDO_NEG = 4;
  /** The default c parameter for SVM. */
  protected static final double DEFAULT_C = 1;
  /** The c parameter for SVM. */
  protected double cParam = DEFAULT_C;
  /** Labeled terms. */
  protected Map<FeatureVector<Integer>, Integer> labeledTerms =
      new HashMap<FeatureVector<Integer>, Integer>();

  /**
   * Default constructor.
   * @param review
   * @throws Exception
   */
  public OnlineSimulatorBowRankSvmLabeledTerms( String review ) throws Exception {
    super( review );
  }
  
  
  /**
   * Turn the Citations into FeatureVectors.
   * @param citations
   * @return
   */
  @Override
  protected Map<PubmedId, FeatureVector<Integer>> createFeatureVectors(
      Set<Citation> citations ) {
    Map<PubmedId, FeatureVector<Integer>> fvs = super.createFeatureVectors( citations );
    File f = new File( "src/test/resources/" + this.dataset + "-labeled-terms.csv" );
    try {
      loadPseudoDocuments( f );
    } catch ( IOException e ) {
      LOG.error( "Could not load labeled psuedodocument terms from " + f.toString(), e );
    }
    
    return fvs;
  }

  /**
   * Load up the psudo documents (labeled terms).
   * @param f
   * @throws IOException
   */
  protected void loadPseudoDocuments( File f ) throws IOException {
    Path path = Paths.get( f.getPath() );
    List<String> terms = Files.readAllLines( path, Charset.defaultCharset() );
    
    for ( String term : terms ) {
      FeatureVector<Integer> pseudo = bow.createUnlabeledFV( PSEUDO_PREFIX + term, term );
      if ( term.contains( "+" ) ) {
        labeledTerms.put( pseudo, PSEUDO_POS );
      } else {
        labeledTerms.put( pseudo, PSEUDO_NEG );
      }
    }
    
    LOG.info( "Loaded " +  labeledTerms.size() + " labeled terms for psueodocuments." );
  }

  @Override
  protected TreeMultimap<Double, PubmedId> rank(
      Map<PubmedId, FeatureVector<Integer>> citations,
      Map<PubmedId, FeatureVector<Integer>> expertRelevantPapers,
      Map<PubmedId, FeatureVector<Integer>> expertIrrelevantPapers ) {
    
    TreeMultimap<Double, PubmedId> rankMap = TreeMultimap.create();

    // pseudo-documents
    for ( FeatureVector<Integer> term : labeledTerms.keySet() ) {
      bow.train( term, labeledTerms.get( term ) );
    }

    // can't classify w/o samples from each class
    if ( !( expertRelevantPapers.isEmpty() || expertIrrelevantPapers.isEmpty() ) ) {
      // create the training data, which is the expert-identified relevant
      // and irrelevant sets
      Map<FeatureVector<Integer>, Integer> minorityMap =
          new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : expertRelevantPapers.values() ) {
        fv.setQid( 1 );
        PubmedId pmid = Util.createOrUpdatePmid( Long.valueOf( fv.getId() ) );
        int pos = POS_L1;
        if ( activeReview.getRelevantLevel2().contains( pmid ) ) pos = POS_L2;
        fv.setRank( pos );
        minorityMap.put( fv, pos );
      }
      Map<FeatureVector<Integer>, Integer> majorityMap =
          new HashMap<FeatureVector<Integer>, Integer>();
      for ( FeatureVector<Integer> fv : expertIrrelevantPapers.values() ) {
        fv.setQid( 1 );
        fv.setRank( NEG );
        majorityMap.put( fv, NEG );
      }

      // create the test set
      TestRelation<Integer> test = new TestRelation<Integer>(
          "test", bow.getTrainingData().getMetadata() );
      for ( FeatureVector<Integer> c : citations.values() ) {
        if ( c.getQid() == null ) c.setQid( 1 );
        test.add( (UnlabeledFeatureVector<Integer>) c );
      }
      
      rankMap = ensembleRank( minorityMap, majorityMap, test );
    } else { // essentially random
      for ( PubmedId c : citations.keySet() ) {
        rankMap.put( 0.0, c );
      }
    }
    return rankMap;
  }
}
