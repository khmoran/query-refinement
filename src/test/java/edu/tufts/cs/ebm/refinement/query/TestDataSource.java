package edu.tufts.cs.ebm.refinement.query;

import java.util.Date;

import org.testng.annotations.Test;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlRow;

import edu.tufts.cs.ebm.review.systematic.SystematicReview;

/**
 * Test connecting to the data source.
 *
 */
public class TestDataSource {
  /**
   * Test the connection.
   * @param args
   */
  @Test
  public void testConnect() {
    String sql = "select 1";
    SqlRow row = Ebean.createSqlQuery( sql ).findUnique();

    assert !row.isEmpty();
  }

  /**
   * Test inserting a review.
   */
  @Test
  public void insertReview() {
    SystematicReview review = new SystematicReview();
    String name = "Clopidogrel SR";
    review.setCreatedOn( new Date() );
    review.setName( name );

    // this will update
    Ebean.save( review );

    // find the inserted entity by its id
    SystematicReview review2 = Ebean.find(
        SystematicReview.class, review.getId() );
    assert review2 != null;
    assert review2.getName().equals( name );

    //Ebean.delete( review );
  }
}
