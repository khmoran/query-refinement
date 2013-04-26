package edu.tufts.cs.ebm.review.systematic;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewReplacementProtonBeam extends
  SimulateReviewWithReplacement {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewReplacementProtonBeam.class );

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    this.statsFile = "stats-pb.csv";
    this.paperRankFile = "ranks-pb.csv";
    this.paperProbFile = "probs-pb.csv";
  }

  /**
   * Tear down the test suite.
   */
  @AfterSuite
  public void tearDown() throws IOException  {
    super.tearDown();
  }
}
