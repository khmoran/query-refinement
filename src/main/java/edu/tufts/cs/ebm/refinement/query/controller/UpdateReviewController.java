package edu.tufts.cs.ebm.refinement.query.controller;

import java.net.URL;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.avaje.ebean.Ebean;

import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;

public class UpdateReviewController implements Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog(
      UpdateReviewController.class );
  /** Key combo to catch TAB + another key. */
  protected final KeyCombination combo = new KeyCodeCombination(
      KeyCode.ENTER );
  /** The query controller. */
  @Autowired
  protected QueryController queryController;
  /** The main controller. */
  @Autowired
  protected MainController mainController;
  /** The pane view. */
  @FXML
  private Pane content;
  /** The name text field. */
  @FXML
  protected TextField nameBox;
  /** The creator text field. */
  @FXML
  protected TextField creatorBox;
  /** The seed combo box. */
  @FXML
  protected TextField seedBox;
  /** The seed list view. */
  @FXML
  protected ListView<PubmedId> listView;
  /** The add seed button. */
  @FXML
  protected Button addSeedButton;
  /** The create button. */
  @FXML
  protected Button createButton;
  /** The name error text. */
  @FXML
  protected Text nameErrorText;
  /** The creator error text. */
  @FXML
  protected Text creatorErrorText;
  /** The seed error text. */
  @FXML
  protected Text seedErrorText;
  /** The review being created. */
  protected SystematicReview activeReview;
  /** The seed list items. */
  protected final ObservableList<PubmedId> listItems =
      FXCollections.observableArrayList();

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
    listView.setItems( listItems );
  }

  /**
   * Handle the "create review" button press.
   *
   * @param e
   */
  @FXML
  protected void handleUpdateButtonAction( ActionEvent e ) {
    if ( nameBox.getText().isEmpty() ) {
      nameErrorText.setText( CreateReviewController.BLANK_NAME );
    } else if ( !nameErrorText.getText().isEmpty() ) {
      nameErrorText.setText( "" );
    }
    if ( creatorBox.getText().isEmpty() ) {
      creatorErrorText.setText( CreateReviewController.BLANK_CREATOR );
    } else if ( !creatorErrorText.getText().isEmpty() ) {
      creatorErrorText.setText( "" );
    }
    if ( !nameBox.getText().isEmpty() && !creatorBox.getText().isEmpty() ) {
      // none of the required fields are empty
      activeReview.setName( nameBox.getText() );
      activeReview.setCreator( creatorBox.getText() );
      Set<PubmedId> seeds = new HashSet<PubmedId>( listItems );
      activeReview.setSeeds( seeds );
      Set<String> props = new HashSet<String>();
      props.add( "seeds" );
      try {
        Ebean.update( activeReview );
      } catch ( javax.persistence.PersistenceException ex ) {
        LOG.error( "Duplicate id error.", ex ); // TODO fix this
      }
      mainController.loadReview( activeReview );
    }
  }

  /**
   * Cancel the update.
   * @param e
   */
  @FXML
  protected void handleCancelButtonAction( ActionEvent e ) {
    mainController.loadReview( activeReview );
  }

  /**
   * Set the active review.
   * @param r
   */
  public void setActiveReview( SystematicReview r ) {
    this.activeReview = r;
    listItems.clear();
    listItems.addAll( activeReview.getSeeds() );
    nameBox.setText( activeReview.getName() );
    creatorBox.setText( activeReview.getCreator() );
  }

  /**
   * Get the view.
   *
   * @return
   */
  public Pane getView() {
    return this.content;
  }

  /**
   * Handle the "add" seed button press.
   *
   * @param e
   */
  @FXML
  protected void handleAddSeedButtonAction( ActionEvent e ) {
    addSeed();
  }


  /**
   * Handle the "add" seed button press.
   *
   * @param e
   */
  @FXML
  protected void handleAddSeedKeyReleased( KeyEvent e ) {
    if ( combo.match( e ) ) {
      addSeed();
    }
  }

  /**
   * Add the seed.
   */
  protected void addSeed() {
    String seedStr = seedBox.getText();

    if ( !seedStr.trim().isEmpty() ) {
      try {
        PubmedId seed = Util.createOrUpdatePmid( Long.valueOf( seedStr ) );
        if ( !listItems.contains( seed ) ) {
          listItems.add( seed );
        }
        if ( seedErrorText.getText().equals(
            CreateReviewController.INVALID_SEED ) ) {
          seedErrorText.setText( "" );
        }
      } catch ( NumberFormatException ex ) {
        seedErrorText.setText( CreateReviewController.INVALID_SEED );
        LOG.error( "Invalid PubMed id entered: " + seedStr, ex );
      }
    }

    seedBox.clear();
  }

  /**
   * Handle deletion of a seed.
   * @param e
   */
  @FXML
  protected void handleDeleteSeedAction( ActionEvent e ) {
    if ( e.getSource() instanceof MenuItem && listView.isFocused() ) {
      PubmedId toDelete =
          listView.getSelectionModel().getSelectedItem();

      if ( toDelete != null ) {
        listItems.remove( toDelete );
        activeReview.getSeeds().remove( toDelete );
        Ebean.update( activeReview );
      }
    }
  }

}
