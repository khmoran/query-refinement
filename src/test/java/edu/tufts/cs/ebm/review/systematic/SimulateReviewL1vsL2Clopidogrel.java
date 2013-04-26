package edu.tufts.cs.ebm.review.systematic;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * Test the MeshWalker class.
 */
public class SimulateReviewL1vsL2Clopidogrel extends SimulateReviewL1vsL2 {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      SimulateReviewL1vsL2Clopidogrel.class );

  /**
   * Set up the test suite.
   * @throws IOException
   * @throws BiffException
   */
  @BeforeSuite
  public void setUp() throws Exception {
    super.setUp();

    this.statsFile = "stats-cl.csv";
    this.paperRankFile = "ranks-cl.csv";
    this.paperProbFile = "probs-cl.csv";
  }

  /**
   * Tear down the test suite.
   */
  @AfterSuite
  public void tearDown() throws IOException  {
    super.tearDown();
  }
}
