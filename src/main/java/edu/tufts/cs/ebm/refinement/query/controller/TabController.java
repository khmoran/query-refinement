package edu.tufts.cs.ebm.refinement.query.controller;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;

import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.animation.RotateTransitionBuilder;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Ellipse;
import javafx.scene.text.Text;
import javafx.util.Duration;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.refinement.query.PicoElement;
import edu.tufts.cs.ebm.refinement.query.PubmedSearcher;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;

public class TabController implements Observer, Initializable {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( TabController.class );
  /** The query (parent) controller. */
  protected QueryController queryController;
  /** The search. */
  protected BooleanProperty searchInProg = new SimpleBooleanProperty( false );
  /** The search. */
  protected BooleanProperty searchOnScreen = new SimpleBooleanProperty( false );
  /** Key combo to catch ENTER + another key. */
  protected final KeyCombination enterCombo = new KeyCodeCombination(
      KeyCode.ENTER );
  /** Key combo to catch ENTER + another key. */
  protected final KeyCombination tabCombo = new KeyCodeCombination(
      KeyCode.TAB );
  /** The article table view. */
  protected ListProperty<Citation> citations = new SimpleListProperty<>(
      FXCollections.observableList( new ArrayList<Citation>() ) );
  /** The search. */
  protected PubmedSearcher searcher;
  /** The search thread. */
  protected Thread searcherThread;
  /** The PICO element corresponding to this tab. */
  protected PicoElement picoElement;
  /** The activeReview. */
  protected SystematicReview activeReview;
  /** The tab view. */
  @FXML
  protected Pane tab;
  /** The stack pane containing the query box. */
  @FXML
  protected StackPane stack;
  /** The number of articles found. */
  @FXML
  protected TableView<Citation> articleTable;
  /** The number of articles found. */
  @FXML
  protected Text numArticles;
  /** The query box. */
  @FXML
  protected TextField queryBox;
  /** The wait indicator rectangle. */
  @FXML
  protected Ellipse timer;
  /** The cancel indicator rectangle. */
  @FXML
  protected Button cancel;
  /** The PubMed id column. */
  @FXML
  protected TableColumn<Citation, PubmedId> pmidCol;
  /** The title column. */
  @FXML
  protected TableColumn<Citation, String> titleCol;
  /** The date column. */
  @FXML
  protected TableColumn<Citation, String> dateCol;
  /** The authors column. */
  @FXML
  protected TableColumn<Citation, String> authorsCol;
  /** The similarity column. */
  @FXML
  protected TableColumn<Citation, Double> similarityCol;

  @Override
  public void initialize( URL location, ResourceBundle resources ) {
    pmidCol.setCellValueFactory( new PropertyValueFactory<Citation, PubmedId>(
        Citation.PMID_ID ) );
    titleCol.setCellValueFactory( new PropertyValueFactory<Citation, String>(
        Citation.TITLE_ID ) );
    dateCol.setCellValueFactory( new PropertyValueFactory<Citation, String>(
        Citation.DATE_ID ) );
    authorsCol.setCellValueFactory( new PropertyValueFactory<Citation, String>(
        Citation.AUTHORS_ID ) );
    similarityCol.setCellValueFactory( null ); // TODO fix this

    citations = new SimpleListProperty<>(
        FXCollections.observableList( new ArrayList<Citation>() ) );

    articleTable.setItems( citations );

    timer.visibleProperty().bindBidirectional( searchInProg );
    cancel.visibleProperty().bindBidirectional( searchInProg );
    numArticles.visibleProperty().bindBidirectional( searchOnScreen );
    numArticles.textProperty().bind( citations.sizeProperty().asString() );

    tab.idProperty().addListener( new IdChangeListener() );
    tab.visibleProperty().addListener( new VisibilityListener() );

    RotateTransition rot = RotateTransitionBuilder.create().node( timer )
        .byAngle( 360 ).duration( new Duration( 3000 ) ).build();
    rot.setCycleCount( Animation.INDEFINITE );
    rot.play();
  }

  /**
   * Get the view.
   * @return
   */
  public Pane getView() {
    return this.tab;
  }

  /**
   * Set the active review.
   * @param activeReview
   */
  protected void setActiveReview( SystematicReview activeReview ) {
    this.activeReview = activeReview;

    if ( activeReview != null ) {
      switch( picoElement ) {
        case POPULATION:
          if ( activeReview.getQueryP() != null ) {
            this.queryBox.setText( activeReview.getQueryP() );
          }
          break;
        case INTERVENTION:
          if ( activeReview.getQueryIC() != null ) {
            this.queryBox.setText( activeReview.getQueryIC() );
          }
          break;
        case OUTCOME:
          if ( activeReview.getQueryO() != null ) {
            this.queryBox.setText( activeReview.getQueryO() );
          }
          break;
        default:
          LOG.error( "No PICO element assigned to Tab with id " + tab.getId() );
          break;
      }

      for ( PubmedId pmid : activeReview.getSeeds() ) {
        MainController.EM.find( PubmedId.class, pmid.getValue() );  // load the seeds
      }
    }
  }

  /**
   * Handle the "submit" button press.
   *
   * @param e
   */
  @FXML
  protected void handleSubmitButtonKeyReleased( KeyEvent e ) {
    if ( enterCombo.match( e ) ) {
      submitQuery();
    }
  }

  /**
   * Handle the "submit" button press.
   *
   * @param e
   */
  @FXML
  protected void handleSubmitButtonAction( ActionEvent e ) {
    submitQuery();
  }

  /**
   * Handle the "cancel" button press.
   *
   * @param e
   */
  @FXML
  protected void handleCancelButtonAction( ActionEvent e ) {
    searcherThread.interrupt();
    searcherThread = null;
    searchInProg.set( false );
  }

  /**
   * Handle the "clear" button press.
   *
   * @param e
   */
  @FXML
  protected void handleClearButtonAction( ActionEvent e ) {
    queryBox.setText( "" );
  }

  /**
   * Handle the "clear" button press.
   *
   * @param e
   */
  @FXML
  protected void handleLoadArticleClickAction( MouseEvent e ) {
    if ( e.getClickCount() > 1 ) {
      Citation c = articleTable.getSelectionModel().getSelectedItem();

      if ( c != null ) {
        queryController.displayCitationDetails( c );
      }
    }
  }

  /**
   * Handle marking a citation relevant.
   *
   * @param e
   */
  @FXML
  protected void handleMarkRelevantAction( ActionEvent e ) {
    Citation c = articleTable.getSelectionModel().getSelectedItem();

    MainController.EM.getTransaction().begin();
    if ( c != null ) {
      activeReview.addRelevantLevel2( c.getPmid() );
      MainController.EM.persist( activeReview );
    }
    MainController.EM.getTransaction().commit();
  }

  /**
   * Handle marking a citation's population irrelevant.
   *
   * @param e
   */
  @FXML
  protected void handleMarkIrrelevantAction( ActionEvent e ) {
    Citation c = articleTable.getSelectionModel().getSelectedItem();

    if ( c != null ) {
      switch( picoElement ) {
        case POPULATION:
          activeReview.addIrrelevantP( c.getPmid() );
          break;
        case INTERVENTION:
          activeReview.addIrrelevantIC( c.getPmid() );
          break;
        case OUTCOME:
          activeReview.addIrrelevantO( c.getPmid() );
          break;
        default:
          LOG.error( "No PICO element assigned to Tab with id " + tab.getId() );
          break;
      }

      MainController.EM.getTransaction().begin();
      MainController.EM.persist( activeReview );
      MainController.EM.getTransaction().commit();
    }
  }

  /**
   * Handle the "details" menu item.
   * @param e
   */
  @FXML
  protected void handleCitationAction( ActionEvent e ) {
    Citation c = articleTable.getSelectionModel().getSelectedItem();

    if ( c != null ) {
      queryController.displayCitationDetails( c );
    }
  }

  /**
   * Transfer the focus from the pane to the query box.
   * @param e
   */
  @FXML
  public void transferFocus( Event e ) {
    if ( e instanceof MouseEvent ||
        ( e instanceof KeyEvent && tabCombo.match( (KeyEvent) e ) ) ) {
      queryBox.requestFocus();
    }
  }

  /**
   * Transfer the focus from the pane to the query box.
   * @param e
   */
  public void transferFocus() {
    queryBox.requestFocus();
  }

  /**
   * Handle the query submission event.
   */
  protected void submitQuery() {
    String query = queryBox.getText();
    LOG.debug( "Submitted query: " + query );

    // first run the query
    try {
      searchOnScreen.set( true );
      searchInProg.set( true );
      citations.clear();

      searcher = new PubmedSearcher( query, activeReview );
      searcher.addObserver( this );
      searcherThread = new Thread( searcher );
      searcherThread.start();
    } catch ( RemoteException e ) {
      LOG.error( "Could not execute search.", e );
    }

    // then update the database
    switch( picoElement ) {
      case POPULATION:
        activeReview.setQueryP( query );
        break;
      case INTERVENTION:
        activeReview.setQueryIC( query );
        break;
      case OUTCOME:
        activeReview.setQueryO( query );
        break;
      default:
        LOG.error( "No PICO element assigned to Tab with id " + tab.getId() );
        break;
    }
    
    MainController.EM.getTransaction().begin();
    MainController.EM.persist( activeReview );
    MainController.EM.getTransaction().commit();
  }

  @Override
  public void update( Observable o, final Object arg ) {
    if ( arg != null ) {
      if ( arg instanceof Collection ) {
        Platform.runLater(
            new Runnable() {
              @Override
              @SuppressWarnings( "unchecked" )
              public void run() {
                Collection<Citation> c = (Collection<Citation>) arg;
                citations.addAll( c );
              }
            }
        );
      }
      if ( arg instanceof Citation && arg != null ) {
        Platform.runLater(
            new Runnable() {
              @Override
              public void run() {
                Citation c = (Citation) arg;
                citations.add( c );
              }
            }
        );
      } else if ( arg instanceof String ) {
        searchInProg.set( false );
      }
    }
  }

  /**
   * Set the query controller.
   * @param qc
   */
  public void setQueryController( QueryController qc ) {
    this.queryController = qc;
  }

  /**
   * The id change listener.
   *
   */
  public class IdChangeListener implements ChangeListener<String> {
    @Override
    public void changed( ObservableValue<? extends String> observable,
        String oldValue, String newValue ) {
      if ( tab.isVisible() ) {
        picoElement = PicoElement.valueOf( newValue.toUpperCase() );
      }
    }
  }

  /**
   * The id change listener.
   *
   */
  public class VisibilityListener implements ChangeListener<Boolean> {

    @Override
    public void changed( ObservableValue<? extends Boolean> observable,
        Boolean oldvalue, Boolean newValue ) {
      if ( newValue ) {
        queryBox.requestFocus();
      }
    }

  }


}
