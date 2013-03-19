package edu.tufts.cs.ebm.refinement.query;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.avaje.ebean.Ebean;

import edu.tufts.cs.ebm.review.systematic.AbstractTest;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;

/**
 * Test connecting to the data source.
 *
 */
public class TestLoadClopidogrelData extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      TestLoadClopidogrelData.class );

  /**
   * Set up the review.
   * @throws Exception
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();
    if ( reviews().isEmpty() || reviews().size() == 1 ) {
      SystematicReview review = new SystematicReview();
      review.setName( "Clopidogrel" );
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
    SystematicReview review = reviews().get( 0 );
    if ( !review.getName().equals( "Clopidogrel" ) ) {
      throw new Exception( "Incorrect review." );
    }

    String pQuery = "clopidogrel OR antiplatelet therapy";
    String icQuery = "cyp2c19 OR platelet reactivity OR verifynow " +
      "OR platelet aggregation";

    review.setQueryP( pQuery );
    review.setQueryIC( icQuery );
    Ebean.update( review );
  }

  /**
   * Test inserting a review.
   * @throws SQLException
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws IOException
   * @throws NumberFormatException
   */
  @Test
  @Parameters( { "seeds" } )
  public void loadSeeds(
      @Optional( "src/test/resources/cl-seeds.txt" ) String seeds )
    throws ClassNotFoundException, NamingException, SQLException,
      NumberFormatException, IOException {
    SystematicReview review = reviews().get( 0 );

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
    Ebean.update( review );
    System.out.println( "Seeds: " + review.getSeeds() );
    System.out.println( "Citations: " + citations().size() );
  }

  /**
   * Test inserting a review.
   */
  @Test
  @Parameters( { "csvFile" } )
  public void loadRelevantIrrelevant(
      @Optional( "src/test/resources/cl-relevant-all.csv" ) String csvFile ) {
    try {
      SystematicReview review = reviews().get( 0 );

      Reader r = new FileReader( csvFile );
      Map<PubmedId, Boolean> pmids = loadPmids( r );

      Set<PubmedId> relevantL1 = new HashSet<>();
      Set<PubmedId> relevantL2 = new HashSet<>();
      for ( PubmedId pmid : pmids.keySet() ) {
        boolean l2 = pmids.get( pmid );

        if ( l2 && !relevantL1.contains( pmid ) ) {
          relevantL1.add( pmid );
          relevantL2.add( pmid );
        } else {
          relevantL1.add( pmid );
        }
      }

      review.setRelevantLevel1( relevantL1 );
      review.setRelevantLevel2( relevantL2 );
      review.setIrrelevantP( new HashSet<PubmedId>() );
      review.setIrrelevantIC( new HashSet<PubmedId>() );
      review.setIrrelevantO( new HashSet<PubmedId>() );

      try {
        Ebean.update( review ); //, props );
      } catch ( javax.persistence.PersistenceException ex ) {
        LOG.error( "Duplicate id error.", ex ); // TODO fix this
      }

      System.out.println( "L1 size: " + review.getRelevantLevel1().size() );
      System.out.println( "L2 size: " + review.getRelevantLevel2().size() );
      System.out.println( "P irrel size: " + review.getIrrelevantP().size() );
      System.out.println( "IC irrel size: " + review.getIrrelevantIC().size() );
      System.out.println( "O irrel size: " + review.getIrrelevantO().size() );
    } catch ( ClassNotFoundException | NamingException
        | SQLException | IOException | ParseException e ) {
      LOG.error( "Could not load reviews.", e );
    }
  }

  /**
   * Load the PMIDs.
   * @return
   * @throws IOException
   * @throws ParseException
   */
  protected Map<PubmedId, Boolean> loadPmids( Reader input )
    throws IOException, ParseException {
    Map<PubmedId, Boolean> pmids = new HashMap<>();

    CSVParser parser = new CSVParser( input, CSVFormat.EXCEL );
    List<CSVRecord> records = parser.getRecords();

    for ( CSVRecord r : records ) {
      if ( r.equals( records.get( 0 ) ) ) {
        continue; // this is the header row
      } else {
        try {
          String pmidStr = r.get( 0 ).trim();

          PubmedId pmid = Util.createOrUpdatePmid( Long.valueOf( pmidStr ) );

          boolean include = (
              r.get( 1 ).trim().toLowerCase().equals( "yes" ) ) ? true : false;

          pmids.put( pmid, include );
        } catch ( NumberFormatException e ) {
          LOG.error( "Could not add entry on line " + r.getRecordNumber() );
        }
      }
    }

    return pmids;
  }
}
