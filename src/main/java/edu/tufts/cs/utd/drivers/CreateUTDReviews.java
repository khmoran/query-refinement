package edu.tufts.cs.utd.drivers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import javax.naming.NamingException;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.Bean;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.PubmedDate;
import edu.tufts.cs.ebm.util.Util;

/**
 * Pubmed query format: ("JOURNAL_NAME"[Journal]) AND ("YEAR"[Date -
 * Publication] : "YEAR"[Date - Publication])
 *
 * @author ke21132
 *
 */
public class CreateUTDReviews {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( CreateUTDReviews.class );

  /**
   * Downloads all of the Pubmed entries for given (journal, year) pairs.
   * 
   * @param args
   * @throws IOException
   * @throws SQLException
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws ParseException
   */
  public static final void main( String[] args ) throws IOException,
      ClassNotFoundException, NamingException, SQLException, ParseException {
    /*
     * Get the (journal, year) pairs to search for
     */
    File dir = new File( "src/main/resources/utd" );
    for ( final File f : dir.listFiles() ) {
      if ( f.getName().endsWith( ".txt" ) ) {
        String name = "UTD "
            + f.getName().replace( "-ref.txt", "" )
                .replace( "negative-training-set-", "" )
                .replace( "positive-training-set-", "" );

        Collection<SystematicReview> reviews = reviews();
        SystematicReview r = null;
        for ( SystematicReview re : reviews ) {
          if ( name.trim().equalsIgnoreCase( re.getName().trim() ) ) {
            r = re;
          }
        }
        if ( r == null ) {
          r = Util.createReview( name, "test" );
        }

        boolean positive = false;
        if ( f.getName().startsWith( "positive" ) ) {
          positive = true;
        }
        readCitations( r, f, positive );
      }
    }
  }

  /**
   * Read in the citations.
   * @param r
   * @param f
   * @param positive
   * @throws ParseException
   */
  protected static void readCitations( SystematicReview r, File f,
      boolean positive ) throws ParseException {
    String posStr  = ( positive ) ? "positive" : "negative";
    LOG.info( "Reading in " + posStr + " citations from " + f.getName() + "..." );
    try {
      String line;
      BufferedReader br = new BufferedReader( new FileReader( f ) );

      long i = 0L;
      while ( ( line = br.readLine() ) != null ) {
        String[] parts = line.split( "\t" );

        PubmedId id = new PubmedId( parts[2] );

        Citation c = Util.getCitation( id );

        if ( c == null ) {
          String journal = parts[0];
          PubmedDate date = null;
          if ( parts[1] != null && !parts[1].isEmpty() ) {
            date = new PubmedDate( PubmedDate.FORMAT.parse( parts[1] ) );
          }
          String title = parts[3];
          String abstr = parts[4];
          String meshStr = parts[5];
          String authors = parts[6];

          ObservableSet<String> meshSet = FXCollections.observableSet();
          for ( String mesh : meshStr.split( "," ) ) {
            meshSet.add( mesh );
          }

          if ( title != null && !title.isEmpty() ) {
            id.setTitle( title );

            try {
              MainController.EM.getTransaction().begin();
              c = new Citation(
                  id, title, abstr, journal, date, authors, meshSet );
              MainController.EM.persist( c );
              MainController.EM.getTransaction().commit();
            } catch ( javax.persistence.PersistenceException ex ) {
              try {
                MainController.EM.getTransaction().begin();
                MainController.EM.merge( c );
                MainController.EM.getTransaction().commit();
              } catch ( javax.persistence.PersistenceException ex2 ) {
                LOG.error( ex2 ); // TODO fix this
              }
            }
          }
        }

        if ( positive ) {
            r.addRelevantLevel2( c.getPmid() );

            MainController.EM.getTransaction().begin();
            MainController.EM.merge( r );
            MainController.EM.getTransaction().commit();
        }
        i++;

        if ( i % 1000 == 0 ) {
          LOG.info( "\tRead " + i + " so far..." );
        }
      }

      br.close();
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  /**
   * Load up the systematic reviews from the database.
   *
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @SuppressWarnings( "unchecked" )
  @Bean
  protected static ObservableList<SystematicReview> reviews()
    throws NamingException, ClassNotFoundException, SQLException {
    Query q = MainController.EM
        .createQuery( "select m from SystematicReview m" );
    List<SystematicReview> list = q.getResultList();

    ObservableList<SystematicReview> reviews = FXCollections
        .observableArrayList( list );

    Collections.sort( reviews );

    return reviews;
  }

}
