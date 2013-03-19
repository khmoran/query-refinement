package edu.tufts.cs.ebm.refinement.query.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;

public class QueryController implements Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( QueryController.class );
  /** The main stage. */
  protected Stage mainStage;
  /** The secondary stage. */
  protected Stage secondaryStage;
  /** The pane view. */
  @FXML
  protected Pane shell;
  /** The name of the active review. */
  @FXML
  protected Text nameText;
  /** The tab pane. */
  @FXML
  protected TabPane tabPane;
  /** The population tab. */
  @FXML
  protected Tab pTab;
  /** The intervention tab. */
  @FXML
  protected Tab icTab;
  /** The outcome tab. */
  @FXML
  protected Tab oTab;
  /** The population tab controller. */
  protected TabController pTabController;
  /** The intervention tab controller. */
  protected TabController icTabController;
  /** The outcome tab controller. */
  protected TabController oTabController;
  /** The activeReview. */
  protected SystematicReview activeReview;
  /** The citation details controller. */
  @Autowired
  protected CitationController citationController;

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
    try {
      FXMLLoader fxmlLoader = new FXMLLoader();
      URL url =  QueryController.class.getClassLoader().getResource(
              "TabPage.fxml" );
      GridPane pane = (GridPane) fxmlLoader.load( url.openStream() );
      pTabController = fxmlLoader.getController();
      pTabController.setQueryController( this );
      pTab.setContent( pane );
      pTab.getContent().setId( pTab.getId().replaceAll( "Tab", "" ) );

      fxmlLoader = new FXMLLoader();
      pane = (GridPane) fxmlLoader.load( url.openStream() );
      icTabController = fxmlLoader.getController();
      icTabController.setQueryController( this );
      icTab.setContent( pane );
      icTab.getContent().setId( icTab.getId().replaceAll( "Tab", "" ) );

      fxmlLoader = new FXMLLoader();
      pane = (GridPane) fxmlLoader.load( url.openStream() );
      oTabController = fxmlLoader.getController();
      oTabController.setQueryController( this );
      oTab.setContent( pane );
      oTab.getContent().setId( oTab.getId().replaceAll( "Tab", "" ) );
    } catch ( IOException e ) {
      LOG.error( "Could not initialize tabs.", e );
    }
  }

  /**
   * Get the view.
   * @return
   */
  public Pane getView() {
    return this.shell;
  }

  /**
   * Display the citation details in the detail pane.
   * @param c
   */
  public void displayCitationDetails( Citation c  ) {
    citationController.setCitation( c );

    if ( secondaryStage == null ) {
      secondaryStage = new Stage();
      secondaryStage.setResizable( false );
      Scene detailScene = new Scene( citationController.getView() );
      secondaryStage.setScene( detailScene );
      secondaryStage.setX( mainStage.getX() + mainStage.getWidth() );
      secondaryStage.setY( mainStage.getY() );
      secondaryStage.setTitle( "Details" );
    }

    secondaryStage.show();
  }

  /**
   * Set the main stage.
   * @param mainStage
   */
  public void setStage( Stage mainStage ) {
    this.mainStage = mainStage;
  }

  /**
   * Set the active review.
   * @param activeReview
   */
  public void setActiveReview( SystematicReview activeReview ) {
    this.activeReview = activeReview;
    this.nameText.setText( activeReview.getName() );
    this.pTabController.setActiveReview( activeReview );
    this.icTabController.setActiveReview( activeReview );
    this.oTabController.setActiveReview( activeReview );
  }

  /**
   * Stop.
   */
  public void stop() {
    if ( secondaryStage != null ) {
      secondaryStage.close();
    }
  }

}
