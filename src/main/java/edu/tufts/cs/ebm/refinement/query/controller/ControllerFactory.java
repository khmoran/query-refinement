package edu.tufts.cs.ebm.refinement.query.controller;
import java.io.IOException;
import java.io.InputStream;

import javafx.fxml.FXMLLoader;

import org.springframework.context.annotation.Bean;



public class ControllerFactory {

  /**
   * The main controller.
   * @return
   * @throws IOException
   */
  @Bean
  public MainController mainController() throws IOException {
    return (MainController) loadController( "MainPage.fxml" );
  }

  /**
   * The create review controller.
   * @return
   * @throws IOException
   */
  @Bean
  public CreateReviewController createReviewController() throws IOException {
    return (CreateReviewController) loadController( "CreateReviewPage.fxml" );
  }

  /**
   * The create review controller.
   * @return
   * @throws IOException
   */
  @Bean
  public UpdateReviewController updateReviewController()
    throws IOException {
    return (UpdateReviewController) loadController(
        "UpdateReviewPage.fxml" );
  }

  /**
   * The review details controller.
   * @return
   * @throws IOException
   */
  @Bean
  public ReviewDetailsController reviewDetailsController()
    throws IOException {
    return (ReviewDetailsController) loadController(
        "ReviewDetailsPage.fxml" );
  }

  /**
   * The create review controller.
   * @return
   * @throws IOException
   */
  @Bean
  public CitationController citationController() throws IOException {
    return (CitationController) loadController( "CitationPage.fxml" );
  }

  /**
   * The query controller.
   * @return
   * @throws IOException
   */
  @Bean
  public QueryController queryController() throws IOException {
    return (QueryController) loadController( "QueryPage.fxml" );
  }

  /**
   * Load the controller.
   * @param url
   * @return
   * @throws IOException
   */
  protected Object loadController( String url ) throws IOException {
    InputStream fxmlStream = null;
    try {
      fxmlStream = ControllerFactory.class.getClassLoader()
          .getResourceAsStream( url );
      FXMLLoader loader = new FXMLLoader();
      loader.load( fxmlStream );
      return loader.getController();
    } finally {
      if ( fxmlStream != null ) {
        fxmlStream.close();
      }
    }
  }
}
