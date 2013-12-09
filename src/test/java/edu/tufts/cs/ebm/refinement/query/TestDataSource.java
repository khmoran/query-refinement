package edu.tufts.cs.ebm.refinement.query;

import java.util.List;

import javax.persistence.Query;

import org.testng.annotations.Test;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;

/**
 * Test connecting to the data source.
 * 
 */
public class TestDataSource {
  /**
   * Test the connection.
   * 
   * @param args
   */
  @Test
  public void testConnect() {
    String sql = "select t from SystematicReview t";
    Query q = MainController.EM.createQuery( sql );
    List<SystematicReview> list = q.getResultList();

    assert list != null;
  }

  /**
   * Test inserting a review.
   */
  @Test
  public void insertReview() {
    // create the review
    SystematicReview review1 = Util.createReview( "Test", "test" );

    // find the inserted entity by its id
    SystematicReview review2 = MainController.EM.find( SystematicReview.class,
        review1.getId() );
    assert review2 != null;
    assert review2.getName().equals( "Test" );

    MainController.EM.getTransaction().begin();
    MainController.EM.remove( review1 );
    MainController.EM.getTransaction().commit();

    // find the inserted entity by its id
    SystematicReview review3 = MainController.EM.find( SystematicReview.class,
        review1.getId() );
    assert review3 == null;
  }
}
