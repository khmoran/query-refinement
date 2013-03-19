package edu.tufts.cs.ebm.review.systematic;

import java.util.HashSet;
import java.util.Set;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.Test;

import edu.tufts.cs.ebm.refinement.query.PubmedIdentifier;
import edu.tufts.cs.ebm.util.Util;

public class CalculateRecall extends AbstractTest {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( CalculateRecall.class );

  /**
   * Calculate recall.
   * @throws AxisFault
   * @throws InterruptedException
   */
  public void testCalculateRecallLevel1( SystematicReview r ) throws AxisFault,
    InterruptedException {
    String popQuery = r.getQueryP();
    String icQuery = r.getQueryIC();
    String query = "(" + popQuery + ") AND (" + icQuery + ")";
    PubmedIdentifier pident = new PubmedIdentifier( query );
    Thread t = new Thread( pident );
    t.start();
    t.join();

    String[] ids = pident.getIds();

    Set<PubmedId> found = new HashSet<>();
    for ( String id : ids ) {
      long l = Long.valueOf( id );
      PubmedId pmid = Util.createOrUpdatePmid( l );

      if ( pmid != null ) {
        if ( r.getRelevantLevel1().contains( pmid ) ) {
          found.add( pmid );
        }
      }
    }

    Set<PubmedId> all = new HashSet<>( r.getRelevantLevel1() );
    all.removeAll( found );

    LOG.info( r.getName() + "[L1]: Ids not found by initial query (" +
        all.size() + "): \n\n" + all.toString() );
  }

  /**
   * Calculate recall.
   * @throws AxisFault
   * @throws InterruptedException
   */
  public void testCalculateRecallLevel2( SystematicReview r ) throws AxisFault,
    InterruptedException {
    String popQuery = r.getQueryP();
    String icQuery = r.getQueryIC();
    String query = "(" + popQuery + ") AND (" + icQuery + ")";
    PubmedIdentifier pident = new PubmedIdentifier( query );
    Thread t = new Thread( pident );
    t.start();
    t.join();

    String[] ids = pident.getIds();

    Set<PubmedId> found = new HashSet<>();
    for ( String id : ids ) {
      long l = Long.valueOf( id );
      PubmedId pmid = Util.createOrUpdatePmid( l );

      if ( pmid != null ) {
        if ( r.getRelevantLevel2().contains( pmid ) ) {
          found.add( pmid );
        }
      }
    }

    Set<PubmedId> all = new HashSet<>( r.getRelevantLevel2() );
    all.removeAll( found );

    LOG.info( r.getName() + "[L2]: Ids not found by initial query (" +
        all.size() + "): \n\n" + all.toString() );
  }

  /**
   * Calculate recall.
   * @throws AxisFault
   * @throws InterruptedException
   */
  @Test
  public void testCalculatePbRecallLevel1() throws AxisFault,
    InterruptedException {
    testCalculateRecallLevel1( protonBeamReview );
    testCalculateRecallLevel2( protonBeamReview );
  }

  /**
   * Calculate recall.
   * @throws AxisFault
   * @throws InterruptedException
   */
  @Test
  public void testCalculateClRecallLevel1() throws AxisFault,
    InterruptedException {
    testCalculateRecallLevel1( clopidogrelReview );
    testCalculateRecallLevel2( clopidogrelReview );
  }
}
