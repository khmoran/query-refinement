package edu.tufts.cs.ebm.review.systematic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import com.google.common.collect.TreeMultimap;

import edu.tufts.cs.ebm.refinement.query.InfoMeasure;
import edu.tufts.cs.ebm.refinement.query.PubmedLocator;
import edu.tufts.cs.ebm.util.MathUtil;
import edu.tufts.cs.ml.DoubleFeature;
import edu.tufts.cs.ml.LabeledFeatureVector;
import edu.tufts.cs.ml.Metadata;
import edu.tufts.cs.ml.TrainRelation;
import edu.tufts.cs.ml.UnlabeledFeatureVector;
import edu.tufts.cs.ml.classify.Classifier;
import edu.tufts.cs.ml.classify.NaiveBayesClassifier;
import edu.tufts.cs.ml.exception.IncomparableFeatureVectorException;
import edu.tufts.cs.similarity.MeshClassifier;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewL1vsL2 extends SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewL1vsL2.class );
  /** The number of papers to propose to the expert per iteration. */
  protected static final int PAPER_PROPOSALS_PER_ITERATION = 5; // 50
  /** The portion of documents to observe. */
  protected static final double PERCENT_TO_OBSERVE = 1; // .9
  /** The weight (cost) given to reading a full paper. */
  protected static final double READ_PAPER_WEIGHT = 5;
  
  protected static final int DEFAULT_K = 3;
  
  protected Metadata metadata = new Metadata();

  /**
   * Get the papers terms to propose.
   * @param query
   * @return
   */
  protected Set<Citation> getPaperProposals( List<Citation> citList,
      Set<Citation> expertRelevantPapers,
      Set<Citation> expertIrrelevantPapers ) {
    Set<Citation> results = new HashSet<>();

    // WITHOUT REPLACEMENT:
    List<Citation> unobserved = new ArrayList<>();
    for ( Citation c : citList ) {
      if ( !expertRelevantPapers.contains( c ) &&
          !expertIrrelevantPapers.contains( c ) ) {
        unobserved.add( c );
      }
    }
    
    // WITH REPLACEMENT:
//    List<Citation> unobserved = citList;

    LOG.info( "Getting paper proposal set from " + unobserved.size() + " papers..." );
//    Set<Integer> rands = MathUtil.uniqueHarmonicRandom(
//        unobserved.size(), PAPER_PROPOSALS_PER_ITERATION );
//    LOG.info( "\tGetting citations at ranks " + rands );
//    for ( int r : rands ) {
//      results.add( unobserved.get( r ) );
//    }
    
    int numToPropose = Math.min( PAPER_PROPOSALS_PER_ITERATION, unobserved.size() );
    
    for ( int i = 0; i < numToPropose; i++ ) {
      results.add( unobserved.get( i ) ); 
    }

    LOG.info(  "Paper proposals: " + results );
    return results;
  }
  
  protected Classifier<Integer> trainClassifier( Set<Citation> results,
      Set<Citation> negClass, Set<Citation> posClass ) {
    Classifier<Integer> cl = new NaiveBayesClassifier<>();
    
    for ( Citation c : results ) {
      for ( String mesh : c.getMeshTerms() ) {
        if ( !metadata.containsKey( mesh ) ) {
          metadata.put( mesh, "Numeric" );
        }
      }
    }

    TrainRelation<Integer> train = new TrainRelation<Integer>( "meshBag", metadata );
    
    for ( Citation c : results ) {
      LabeledFeatureVector<Integer> lfv;
      if ( negClass.contains( c ) ) {
        lfv = new LabeledFeatureVector<>( 0, c.getPmid().toString() );
      } else if ( posClass.contains( c ) ) {
        lfv = new LabeledFeatureVector<>( 1, c.getPmid().toString() );
      } else {
        continue;
      }
      for ( String mesh : metadata.keySet() ) {
        double val = ( c.getMeshTerms().contains( mesh ) ) ? 1 : 0;
        DoubleFeature bagFeat = new DoubleFeature( mesh, val );
        lfv.put( mesh, bagFeat );
      }
      train.add( lfv );
    }

    cl.train( train );
    
    return cl;
  }
  
  protected double rankWithClassifier( Classifier<Integer> cl, Citation c ) {
    UnlabeledFeatureVector<Integer> ufv = new UnlabeledFeatureVector<>(
        c.getPmid().toString() );
    
    for ( String mesh : metadata.keySet() ) {
      double val = ( c.getMeshTerms().contains( mesh ) ) ? 1 : 0;
      DoubleFeature bagFeat = new DoubleFeature( mesh, val );
      ufv.put( mesh, bagFeat );
    }

    try {
      cl.classify( ufv );
      Integer classification = ufv.getClassification( 3 );

//      boolean correct = false;
//      if ( ( classification == 1 &&
//          activeReview.getRelevantLevel2().contains( c.getPmid() ) )
//        || ( classification == 0 &&
//          activeReview.getRelevantLevel1().contains( c.getPmid() ) ) ) {
//          correct = true;
//      }
//      System.out.println( c.getPmid() + " classified as " + classification + "; correct? " + correct );
      
      if ( classification != null ) {
        double certainty = cl.getCertainty( ufv );
  
        if ( classification == 1 ) {
          return classification + certainty;
        } else {
          return classification - certainty;
        }
      }
    } catch ( IncomparableFeatureVectorException e ) {
      LOG.error( e );
    }
    
    return 0;
  }

  /**
   * Rank the query results using cosine similarity.
   * @param searcher
   * @return
   */
  protected List<Citation> rank( Set<Citation> results,
      Set<Citation> proposals, MeshClassifier meshClassifier,
      Set<Citation> negClass, Set<Citation> posClass ) {
    TreeMultimap<Double, Citation> rankMap = TreeMultimap.create();
    
    Classifier<Integer> cl = trainClassifier( results, negClass, posClass );

    for ( Citation c : results ) {
      if ( !negClass.isEmpty() && !posClass.isEmpty() ) {
        // no need to classify the training data :)
        if ( negClass.contains( c ) ) {
          rankMap.put( -100.0, c );
        } else if ( posClass.contains( c ) ) {
          rankMap.put( 100.0, c );
        } else {
          rankMap.put( rankWithClassifier( cl, c ), c );
        }
      } else {
        rankMap.put( 1.0, c ); //random
      }
    }

    List<Citation> ranks = new ArrayList<>();
    for ( Double d : rankMap.keySet().descendingSet() ) {
      for ( Citation c : rankMap.get( d ) ) {
        ranks.add( c );
      }
    }
    
//    TreeMultimap<Double, Citation> cosineMap = TreeMultimap.create();
//    for ( Citation c : results ) {
//      cosineMap.put( c.getSimilarity(), c );
//    }
//
//    List<Citation> ranks = new ArrayList<>();
//    for ( Double d : cosineMap.keySet().descendingSet() ) {
//      for ( Citation c : cosineMap.get( d ) ) {
//        ranks.add( c );
//      }
//    }

    // record the ranks
    recordRank( ranks, proposals );

    return ranks;
  }

  /**
   * Record the current ranking.
   * @param cosineMap
   */
  protected void recordRank( List<Citation> ranks,
     Set<Citation> proposals ) {

    // get probability information
    if ( z == -1 ) {
      z = calcZ( ranks.size() );
    }

    int rank = 1;
    for ( Citation c : ranks ) {
      PubmedId pmid = c.getPmid();
        
      String rankStr = rankOutput.get( pmid );
      if ( rankStr == null ) {
        rankStr = "";
      } else {
        rankStr += ",";
      }
      rankStr += String.valueOf( rank );
      rankOutput.put( pmid, rankStr );

      double prob = (double) 1/ (double) rank / z;
      prob = MathUtil.round( prob, 7 );

      String probStr = probOutput.get( pmid );
      if ( probStr == null ) {
        probStr = "";
      } else {
        probStr += ",";
      }
      probStr += String.valueOf( prob );
      probOutput.put( pmid, probStr );

      rank++;
    }

    for ( Citation c : proposals ) {
      String observStr = observOutput.get( c.getPmid() );
      if ( observStr == null ) {
        observStr = "";
      } else {
        observStr += ",";
      }
      observStr += String.valueOf( iteration );
      observOutput.put( c.getPmid(), observStr );
    }

    iteration++;
  }
  
  /**
   * Propose the papers to the expert and add them to the appropriate bins:
   * relevant and irrelevant.
   * @param proposals
   * @param relevant
   * @param irrelevant
   */
  protected Set<Citation> proposePapers( Set<Citation> proposals ) {
    LOG.info( "Proposing papers..." );
    Set<Citation> newRelevant = new HashSet<>();
    for ( Citation c : proposals ) {
      if ( activeReview.getRelevantLevel2().contains( c.getPmid() ) ) {
        LOG.debug( "\t" + c.getTitle() + " [" + c.getPmid() + "] is relevant" );
        newRelevant.add( c );
      }
    }

    LOG.info( "Accepting papers: " + newRelevant );
    return newRelevant;
  }
  
  protected Set<Citation> getL1s() {
    Set<Citation> results = null;
    try {
      PubmedLocator loc = new PubmedLocator();
      results = loc.getCitations( activeReview.getRelevantLevel1() );
    } catch ( AxisFault e ) {
      LOG.error( e );
    }
    return results;
  }

  /**
   * Simulate the Clopidogrel query refinement process.
   *
   * @throws InterruptedException
   * @throws IOException
   */
  @Test
  @Override
  public void simulateReview()
    throws InterruptedException, IOException {
    // prepare the CSV output
    FileWriter fw = new FileWriter( statsFile );
    BufferedWriter out = new BufferedWriter( fw );
   // header row
    out.write(
      "i,cost,#L2 observed,L2recall" );
    out.newLine();
    out.flush();

    StringBuffer popQuery = new StringBuffer( activeReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( activeReview.getQueryIC() );

    LOG.info( "Initial POPULATION query: " + popQuery );
    LOG.info( "Initial INTERVENTION query: " + icQuery );

    Set<Citation> expertL1 = new HashSet<>();
    Set<Citation> expertL2 = new HashSet<>();
    
    expertL2.addAll( activeReview.getSeedCitations() );
    
    MeshClassifier meshClassifier = new MeshClassifier();
    
    Set<Citation> results = getL1s();
    
    // set the initial similarities
    updateSimilarities( results, expertL2 );

    // gather initial statistics on the results
    List<Citation> ranks = rank( results, new HashSet<Citation>(),
        meshClassifier, expertL1, expertL2 );
    
    int numPapersToObserve = (int) Math.ceil(
        results.size() * PERCENT_TO_OBSERVE );
    
    LOG.info( "Observing at least " + PERCENT_TO_OBSERVE +
        " of available papers ("+ numPapersToObserve + ")" );

    int i = 0;
    int numPapersRead = 0;
    while ( ( expertL2.size() + expertL1.size() ) < numPapersToObserve ) {
      LOG.info(  "\n\nIteration " + ++i + ":\n" );

      Set<Citation> paperProposals = getPaperProposals( ranks,
          expertL2, expertL1 );

      int numRelevant = expertL2.size();
      if ( paperProposals.isEmpty() ) {
        LOG.info( "No new papers to propose." );
        break;
      } else {
        Set<Citation> accepted = proposePapers( paperProposals );
        
        for ( Citation c : paperProposals ) {
          if ( accepted.contains( c ) ) {
            expertL2.add( c );
          } else {
            expertL1.add( c );
          }
        }

        // if new papers are accepted, update the similarities, infoGain,
        // and rankings
        if ( expertL2.size() > numRelevant ) {
          updateSimilarities( results, expertL2 ); // update cosine
          if ( expertL2.size() > 0 ) {
            //meshClassifier.update( expertL2, expertL1 ); // BACKWARDS
            meshClassifier.update( expertL1, expertL2 ); // update mesh
          }
          ranks = rank( results, paperProposals, meshClassifier, expertL1, expertL2 ); // rank
        } else { // either way record the ranks
          recordRank( ranks, paperProposals );
        }

        LOG.debug( "\t# total L2 observed: " + expertL2.size() );
        LOG.debug( "\t# total L1 observed: " + expertL1.size() );
      }

      //Map<String, InfoMeasure> im = evaluateQuery( searcher, ranks, expertAllRelevant,
      //    expertIrrelevantPapers );
      // write out the current stats
      InfoMeasure im = new InfoMeasure( expertL2.size(),
          activeReview.getRelevantLevel2().size() );
      LOG.info( "\tL2 recall: " + MathUtil.round( im.getRecall(), 4) );
      // the cost of reading all of the titles + abstracts so far
      double costL1 = expertL2.size() + expertL1.size();
      // the cost of reading the full papers so far
      double costL2 = READ_PAPER_WEIGHT * numPapersRead;
      out.write( i + "," + (costL1 + costL2) + "," +
          expertL2.size() + "," + im.getRecall() );

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
    super.tearDown();
  }
}
