package edu.tufts.cs.ebm.refinement.query;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.avaje.ebean.Ebean;
import com.scireum.open.commons.Value;
import com.scireum.open.xml.NodeHandler;
import com.scireum.open.xml.StructuredNode;

import edu.tufts.cs.ebm.review.systematic.AbstractTest;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;

/**
 * Test the MeshWalker class.
 */
public class TestLoadProtonBeamData extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      TestLoadProtonBeamData.class );

  /**
   * Set up the review.
   * @throws Exception
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();
    if ( reviews().isEmpty() || reviews().size() == 1 ) {
      SystematicReview review = new SystematicReview();
      review.setName( "Proton Beam Therapy" );
      review.setCreator( "test" );
      Ebean.save( review );
    }
  }

  /**
   * Set the initial query.
   * @throws Exception
   */
  @Test
  public void loadQueries() throws Exception {
    SystematicReview review = reviews().get( 1 );
    if ( !review.getName().equals( "Proton Beam Therapy" ) ) {
      throw new Exception( "Incorrect review." );
    }

    String pQuery = "Cancer";
    String icQuery = "Brachytherapy[MH] OR \"Neutron Capture Therapy\"[MH] " +
      "OR Proton Therapy OR Radiotherapy, High-Energy OR Proton Beam OR " +
      "Charged Particle Therapy OR Proton Irradiation OR Helium Irradiation " +
      "OR Ion Radiotherapy";

    review.setQueryP( pQuery );
    review.setQueryIC( icQuery );
    Ebean.update( review );
  }

  /**
   * Test inserting a review.
   * @throws Exception
   */
  @Test
  @Parameters( { "seeds" } )
  public void loadSeeds(
      @Optional( "src/test/resources/pb-seeds.txt" ) String seeds )
    throws Exception {
    SystematicReview review = reviews().get( 1 );
    if ( !review.getName().equals( "Proton Beam Therapy" ) ) {
      throw new Exception( "Incorrect review." );
    }

    BufferedReader br = new BufferedReader( new FileReader( seeds ) );
    Set<PubmedId> seedSet = new HashSet<>();
    String line;
    while ( ( line = br.readLine() ) != null ) {
      String idStr = line.trim();
      long id = Long.valueOf( idStr );
      PubmedId pmid = Util.createOrUpdatePmid( id );
      seedSet.add( pmid );
    }
    br.close();

    review.setSeeds( seedSet );
    System.out.println( "Seeds: " + review.getSeeds() );
    System.out.println( "Citations: " + citations().size() );
    Ebean.update( review );
  }

  /**
   * Test loading a MeSH term.
   * @throws Exception
   */
  @Test
  @Parameters( { "relevantFile" } )
  public void testLoadLevel1Relevant(
      @Optional( "src/test/resources/pb-relevant-l1.txt" )
      String relevantFile )
    throws Exception {
    SystematicReview review = reviews().get( 1 );
    if ( !review.getName().equals( "Proton Beam Therapy" ) ) {
      throw new Exception( "Incorrect review." );
    }

    Set<PubmedId> relevant = new HashSet<>();
    BufferedReader br = new BufferedReader( new FileReader( relevantFile ) );
    String line;
    while ( ( line = br.readLine() ) != null ) {
      String idStr = line.trim();
      long id = Long.valueOf( idStr );
      PubmedId pmid = Util.createOrUpdatePmid( id );
      if ( !relevant.contains( pmid ) ) {
        relevant.add( pmid );
      } else {
        LOG.debug( "Detected duplicate: " + pmid );
      }
    }
    br.close();

    System.out.println( "L1 size: " + relevant.size() );
    review.setRelevantLevel1( relevant );
    try {
      Ebean.update( review ); //, props );
    } catch ( javax.persistence.PersistenceException ex ) {
      LOG.error( "Duplicate id error.", ex ); // TODO fix this
    }
  }

  /**
   * Test loading a MeSH term.
   * @throws Exception
   */
  @Test
  @Parameters( { "relevantFile" } )
  public void testLoadLevel2Relevant(
      @Optional( "src/test/resources/pb-relevant-l2.txt" )
      String relevantFile )
    throws Exception {
    SystematicReview review = reviews().get( 1 );
    if ( !review.getName().equals( "Proton Beam Therapy" ) ) {
      throw new Exception( "Incorrect review." );
    }

    Set<PubmedId> relevant = new HashSet<>();
    BufferedReader br = new BufferedReader( new FileReader( relevantFile ) );
    String line;
    while ( ( line = br.readLine() ) != null ) {
      String idStr = line.trim();
      long id = Long.valueOf( idStr );
      PubmedId pmid = Util.createOrUpdatePmid( id );
      if ( !review.getRelevantLevel1().contains( pmid ) ) {
        LOG.debug( "Found an L2 missing from L1: " + pmid );
      }
      if ( !relevant.contains( pmid ) ) {
        relevant.add( pmid );
      } else {
        LOG.debug( "Detected duplicate: " + pmid );
      }
    }
    br.close();

    System.out.println( "L2 size: " + relevant.size() );
    review.setRelevantLevel2( relevant );
    try {
      Ebean.update( review ); //, props );
    } catch ( javax.persistence.PersistenceException ex ) {
      LOG.error( "Duplicate id error.", ex ); // TODO fix this
    }
  }

  public class RecordHandler implements NodeHandler {
    /** The "rec-number" node name. */
    protected static final String REC_NUMBER_NODE_NAME = "rec-number";
    /** The "notes" node name. */
    protected static final String NOTES_NODE_NAME = "notes";
    /** The document ids. */
    protected Collection<Long> docIds;
    /** The terms to find. */
    protected Collection<PubmedId> ids = new HashSet<PubmedId>();
    /** The terms found. */
    protected Set<String> relevant = new HashSet<>();
    /** The number of nodes. */
    protected int numNodes = 0;

    /**
     * Default constructor.
     * @param terms
     */
    public RecordHandler( Collection<Long> records ) {
      this.docIds = records;
    }

    /**
     * Get the found pmids.
     * @return
     */
    public Collection<PubmedId> getPmids() {
      return this.ids;
    }

    /**
     * Get the number of nodes.
     * @return
     */
    public int getNumNodes() {
      return this.numNodes;
    }

    @Override
    public void process( StructuredNode node ) {
      try {
        numNodes++;
        Value v = node.queryValue( REC_NUMBER_NODE_NAME );
        if ( docIds.contains( Long.valueOf( v.toString() ) ) ) {
          String notes = node.queryValue( NOTES_NODE_NAME ).toString();
          int idx = 0;
          for ( int i = 0; i < notes.length(); i++ ) {
            if ( !Character.isDigit( notes.charAt( i ) ) ) {
              idx = i;
              break;
            }
          }
          if ( idx > 0 ) {
            String idStr = notes.substring( 0, idx );
            long id = Long.valueOf( idStr );
            PubmedId pmid = Util.createOrUpdatePmid( id );

            if ( pmid != null ) {
              ids.add( pmid );
            } else {
              LOG.warn( "No pmid found for relevant record #" + v.toString() +
                " in notes '" + notes + "'\n\tTitle: " +
                  node.queryValue( "titles" ) );
            }
          } else {
            LOG.warn( "No pmid found for relevant record #" + v.toString() +
              " in notes '" + notes + "'\n\tTitle: " +
                node.queryValue( "titles" ) );
          }
        }
      } catch ( XPathExpressionException e ) {
        LOG.error( e );
      }
    }
  }

}
