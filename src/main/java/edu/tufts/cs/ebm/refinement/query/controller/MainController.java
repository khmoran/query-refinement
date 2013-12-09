package edu.tufts.cs.ebm.refinement.query.controller;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import edu.tufts.cs.ebm.review.systematic.SystematicReview;

public class MainController implements Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( MainController.class );
  public static final EntityManagerFactory FACTORY = Persistence
      .createEntityManagerFactory( "pubmed" );
  public static final EntityManager EM = FACTORY.createEntityManager();
  /** The main stage. */
  protected Stage mainStage;
  /** The query controller. */
  @Autowired
  protected QueryController queryController;
  /** The create review controller. */
  @Autowired
  protected CreateReviewController createReviewController;
  /** The configure review controller. */
  @Autowired
  protected UpdateReviewController configureReviewController;
  /** The review details controller. */
  @Autowired
  protected ReviewDetailsController reviewDetailsController;
  /** The pane view. */
  @FXML
  private BorderPane view;
  /** The content view. */
  @FXML
  private GridPane content;
  /** The review menu. */
  @FXML
  protected Menu reviewMenu;
  /** The load review table. */
  @FXML
  protected TableView<SystematicReview> loadTable;
  /** The welcome text. */
  @FXML
  protected Text welcomeText;
  /** The create review text. */
  @FXML
  protected Text createReviewText;
  /** The load review text. */
  @FXML
  protected Text loadReviewText;
  /** The create button text. */
  @FXML
  protected Button createButton;
  /** The load button text. */
  @FXML
  protected Button loadButton;
  /** The name column. */
  @FXML
  protected TableColumn<SystematicReview, String> nameCol;
  /** The creator column. */
  @FXML
  protected TableColumn<SystematicReview, String> creatorCol;
  /** The date created column. */
  @FXML
  protected TableColumn<SystematicReview, String> createdOnCol;

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
    ObservableList<SystematicReview> reviews = null;

    try {
      reviews = reviews();
    } catch ( ClassNotFoundException | NamingException | SQLException e ) {
      LOG.error( "Could not connect to database.", e );
    }

    loadTable.setItems( reviews );
  }

  /**
   * Load up the systematic reviews from the database.
   * 
   * @return
   * @throws NamingException
   * @throws ClassNotFoundException
   * @throws SQLException
   */
  @Bean
  protected ObservableList<SystematicReview> reviews() throws NamingException,
      ClassNotFoundException, SQLException {

    Query q = MainController.EM
        .createQuery( "select m from SystematicReview m" );
    List<SystematicReview> list = q.getResultList();
    ObservableList<SystematicReview> reviews = FXCollections
        .observableArrayList( list );

    return reviews;
  }

  /**
   * Get the view.
   * 
   * @return
   */
  public Pane getView() {
    return this.view;
  }

  /**
   * Handle the "create review" button press.
   * 
   * @param e
   */
  @FXML
  protected void handleCreateReviewButtonAction( ActionEvent e ) {
    this.mainStage.hide();
    view.setCenter( createReviewController.getView() );
    this.mainStage
        .setWidth( createReviewController.getView().getPrefWidth() + 40 );
    this.mainStage
        .setHeight( createReviewController.getView().getPrefHeight() + 40 );
    this.mainStage.centerOnScreen();
    this.mainStage.show();
  }

  /**
   * Handle the "create review" button press.
   * 
   * @param e
   */
  @FXML
  protected void handleDeleteReviewAction( ActionEvent e ) {
    if ( e.getSource() instanceof MenuItem && loadTable.isFocused() ) {
      List<SystematicReview> toDelete = loadTable.getSelectionModel()
          .getSelectedItems();

      for ( SystematicReview s : toDelete ) {
        MainController.EM.remove( s );

        try {
          loadTable.setItems( reviews() );
        } catch ( ClassNotFoundException | NamingException | SQLException ex ) {
          LOG.error( "Could not connect to database.", ex );
        }
      }
    }
  }

  /**
   * Handle the "load review" button press.
   * 
   * @param e
   */
  @FXML
  protected void handleLoadReviewButtonAction( ActionEvent e ) {
    SystematicReview r = loadTable.getSelectionModel().getSelectedItem();

    if ( r != null ) {
      loadReview( r );
    }
  }

  /**
   * Handle the "load review" double click.
   * 
   * @param e
   */
  @FXML
  protected void handleLoadReviewClickAction( MouseEvent e ) {
    if ( e.getClickCount() > 1 ) {
      SystematicReview r = loadTable.getSelectionModel().getSelectedItem();

      if ( r != null ) {
        loadReview( r );
      }
    }
  }

  /**
   * Handle the "home" menu item.
   * 
   * @param e
   */
  @FXML
  protected void handleMenuHomeAction( ActionEvent e ) {
    // refresh the list of reviews
    try {
      loadTable.setItems( reviews() );
    } catch ( ClassNotFoundException | NamingException | SQLException ex ) {
      LOG.error( "Could not connect to database.", ex );
    }

    this.mainStage.hide();
    view.setCenter( this.content );
    this.mainStage.setWidth( this.view.getPrefWidth() );
    this.mainStage.setHeight( this.view.getPrefHeight() );
    this.mainStage.centerOnScreen();
    this.mainStage.show();
  }

  /**
   * 
   * @param e
   */
  @FXML
  protected void handleMenuExitAction( ActionEvent e ) {
    Platform.exit();
  }

  /**
   * Handle the "configure" menu item.
   * 
   * @param e
   */
  @FXML
  protected void handleMenuConfigureAction( ActionEvent e ) {
    this.mainStage.hide();
    view.setCenter( configureReviewController.getView() );
    this.mainStage
        .setWidth( configureReviewController.getView().getPrefWidth() + 40 );
    this.mainStage.setHeight( configureReviewController.getView()
        .getPrefHeight() + 40 );
    this.mainStage.centerOnScreen();
    this.mainStage.show();
  }

  /**
   * Handle the "configure" menu item.
   * 
   * @param e
   */
  @FXML
  protected void handleMenuDetailsAction( ActionEvent e ) {
    this.mainStage.hide();
    view.setCenter( reviewDetailsController.getView() );
    this.mainStage
        .setWidth( reviewDetailsController.getView().getPrefWidth() + 40 );
    this.mainStage
        .setHeight( reviewDetailsController.getView().getPrefHeight() + 40 );
    this.mainStage.centerOnScreen();
    this.mainStage.show();
  }

  /**
   * Load the given review.
   * 
   * @param r
   */
  public void loadReview( SystematicReview r ) {
    reviewMenu.setDisable( false );
    queryController.setActiveReview( r );
    configureReviewController.setActiveReview( r );
    reviewDetailsController.setActiveReview( r );
    this.mainStage.hide();
    view.setCenter( queryController.getView() );
    this.mainStage.setWidth( queryController.getView().getPrefWidth() );
    this.mainStage.setHeight( queryController.getView().getPrefHeight() );
    this.mainStage.centerOnScreen();
    this.mainStage.show();
  }

  /**
   * Set the Stage.
   * 
   * @param stage
   */
  public void setStage( Stage stage ) {
    this.mainStage = stage;
    this.queryController.setStage( stage ); // TODO this is a hack
  }

}
