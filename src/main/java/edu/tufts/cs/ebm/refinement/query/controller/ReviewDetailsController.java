package edu.tufts.cs.ebm.refinement.query.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;

public class ReviewDetailsController implements Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      ReviewDetailsController.class );
  /** The main controller. */
  @Autowired
  protected MainController mainController;
  /** The active review. */
  protected SystematicReview activeReview;
  /** The pane view. */
  @FXML
  protected GridPane content;
  /** The name of the review. */
  @FXML
  protected Text nameContent;
  /** The date the review was created. */
  @FXML
  protected Text createdOnContent;
  /** The creator of the review. */
  @FXML
  protected Text creatorContent;
  /** The seeds for the review. */
  @FXML
  protected Text seeds;
  /** The relevant articles for the review. */
  @FXML
  protected Text relevant;
  /** The irrelevant population articles for the review. */
  @FXML
  protected Text irrelevant;
  /** The seed tab. */
  @FXML
  protected Tab seedTab;
  /** The relevant tab. */
  @FXML
  protected Tab relevantTab;
  /** The irrelevant tab. */
  @FXML
  protected Tab irrelevantTab;

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
  }

  /**
   * Get the view.
   * @return
   */
  public GridPane getView() {
    return this.content;
  }

  /**
   * Set the active review.
   * @param clopidogrelReview
   */
  public void setActiveReview( SystematicReview r ) {
    this.activeReview = r;
    if ( activeReview.getName() != null ) {
      this.nameContent.setText( activeReview.getName() );
    } else {
      this.nameContent.setText( "" );
    }
    if ( activeReview.getCreatedOn() != null ) {
      this.createdOnContent.setText( activeReview.getCreatedOn().toString() );
    } else {
      this.createdOnContent.setText( "" );
    }
    if ( activeReview.getCreator() != null ) {
      this.creatorContent.setText( activeReview.getCreator() );
    } else {
      this.creatorContent.setText( "" );
    }
    if ( activeReview.getSeeds() != null  ) {
      this.seeds.setText( buildString( activeReview.getSeeds() ) );
      seedTab.setText( "Seeds (" + activeReview.getSeeds().size() + ")" );
    }
    if ( activeReview.getRelevantLevel2() != null  ) {
      this.relevant.setText( buildString( activeReview.getRelevantLevel2() ) );
      relevantTab.setText(
          "Relevant (" + activeReview.getRelevantLevel2().size() + ")" );
    }

    Set<PubmedId> irrels = new HashSet<>( activeReview.getIrrelevantP() );
    irrels.addAll( activeReview.getIrrelevantIC() );
    irrels.addAll( activeReview.getIrrelevantO() );
    this.irrelevant.setText( buildString( irrels ) );
    irrelevantTab.setText( "Irrelevant (" + irrels.size() + ")" );
  }

  /**
   * Build a String from the list of PubmedIds.
   * @param ids
   * @return
   */
  protected String buildString( Set<PubmedId> ids ) {
    StringBuilder seedList = new StringBuilder();
    List<PubmedId> sorted = new ArrayList<>( ids );
    Collections.sort( sorted );
    for ( PubmedId seed : sorted ) {
      if ( seed.getTitle() != null && !seed.getTitle().isEmpty() ) {
        seedList.append(
            "\"" + seed.getTitle() + "\" (" + seed.toString() + ")\n" );
      } else {
        seedList.append( seed.toString() + "\n" );
      }
    }

    return seedList.toString();
  }

  /**
   * Handle the "done" button press.
   * @param e
   */
  @FXML
  protected void handleDoneButtonAction( ActionEvent e ) {
    mainController.loadReview( activeReview );
  }
}
