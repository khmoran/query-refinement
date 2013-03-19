package edu.tufts.cs.ebm.refinement.query.controller;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.Citation;

public class CitationController implements Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      CitationController.class );
  /** The date format. */
  protected static final DateFormat DATE_FORMAT =
      new SimpleDateFormat( "MM/dd/yyyy" );
  /** The citation. */
  protected Citation citation;
  /** The pane view. */
  @FXML
  protected ScrollPane content;
  /** The title of the article. */
  @FXML
  protected Text titleContent;
  /** The authors of the article. */
  @FXML
  protected Text authorsContent;
  /** The date of the article. */
  @FXML
  protected Text dateContent;
  /** The journal in which the article was published. */
  @FXML
  protected Text journalContent;
  /** The MeSH terms for the article. */
  @FXML
  protected Text meshContent;
  /** The abstract for the article. */
  @FXML
  protected Text abstractContent;
  /** The PubMed id for the article. */
  @FXML
  protected Text pmidContent;

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
  }

  /**
   * Get the view.
   * @return
   */
  public ScrollPane getView() {
    return this.content;
  }

  /**
   * Set the citation.
   * @param c
   */
  public void setCitation( Citation c ) {
    this.citation = c;
    if ( c.getTitle() != null ) {
      this.titleContent.setText( c.getTitle() );
    } else {
      this.titleContent.setText( "" );
    }
    if ( c.getAuthors() != null ) {
      this.authorsContent.setText( c.getAuthors() );
    } else {
      this.titleContent.setText( "" );
    }
    if ( c.getDate() != null ) {
      this.dateContent.setText( DATE_FORMAT.format( c.getDate() ) );
    } else {
      this.dateContent.setText( "" );
    }
    if ( c.getJournal() != null ) {
      this.journalContent.setText( c.getJournal() );
    } else {
      this.journalContent.setText( "" );
    }
    if ( c.getMeshTerms() != null ) {
      this.meshContent.setText( c.getMeshTerms().toString() );
    } else {
      this.meshContent.setText( "" );
    }
    if ( c.getAbstr() != null ) {
      this.abstractContent.setText( c.getAbstr() );
    } else {
      this.abstractContent.setText( "" );
    }
    if ( c.getPmid() != null ) {
      this.pmidContent.setText( c.getPmid().toString() );
    } else {
      this.pmidContent.setText( "" );
    }
  }
}
