package edu.tufts.cs.ebm.mesh;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import edu.tufts.cs.ebm.refinement.query.PicoElement;

/**
 * Test loading the MeSH rankings.
 */
public class TestLoadMeshRanking {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory
      .getLog( TestLoadMeshRanking.class );

  /**
   * Load the PMIDs.
   * 
   * @return
   * @throws IOException
   * @throws ParseException
   */
  @Test
  @Parameters({ "csvFile" })
  public void loadInfoGainsTest(
      @Optional("src/test/resources/cl-mesh-ranking-major.out") String csvFile )
      throws IOException, ParseException {

    Reader r = new FileReader( csvFile );
    TreeSet<RankedMesh> rankedMeshes = loadInfoGains( r );

    LOG.info( "# terms: " + rankedMeshes.size() );

    for ( RankedMesh rm : rankedMeshes.descendingSet() ) {
      System.out.println( rm );
    }
  }

  /**
   * Load the info gain data.
   * 
   * @param input
   * @return
   * @throws IOException
   * @throws ParseException
   */
  public TreeSet<RankedMesh> loadInfoGains( Reader input ) throws IOException,
      ParseException {
    CSVParser parser = new CSVParser( input, CSVFormat.TDF );
    List<CSVRecord> records = parser.getRecords();

    TreeSet<RankedMesh> meshes = new TreeSet<>();
    for ( CSVRecord r : records ) {
      double infoGain = Double.parseDouble( r.get( 0 ) );
      String[] parts = r.get( 1 ).split( ":" );
      String term = parts[1].trim();
      boolean isPos = parts[0].contains( "+" ) ? true : false;

      RankedMesh rm = new RankedMesh( term, infoGain, isPos,
          PicoElement.POPULATION );
      meshes.add( rm );
    }

    return meshes;
  }
}
