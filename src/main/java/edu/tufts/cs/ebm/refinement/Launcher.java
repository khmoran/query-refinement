package edu.tufts.cs.ebm.refinement;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import edu.tufts.cs.ebm.refinement.query.controller.ControllerFactory;
import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.refinement.query.controller.QueryController;

public class Launcher extends Application {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( Launcher.class );
  /** The JAVA_OPTS environment variable. */
  public static final String JAVA_OPTS_ENV = "JAVA_OPTS";

  /**
   * Main to launch the application.
   * 
   * @param args
   */
  public static void main( String[] args ) {
    launch( args );
  }

  /**
   * Launch the application.
   */
  @Override
  public void start( Stage stage ) throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
        ControllerFactory.class );
    MainController main = context.getBean( MainController.class );
    Scene scene = new Scene( main.getView() );
    scene.getStylesheets().add( "query.css" );
    stage.setScene( scene );
    stage.centerOnScreen();
    stage.setTitle( "PubMed Query Refinement Engine" );
    stage.setResizable( false );
    main.setStage( stage );
    stage.show();

    // housekeeping
    setProxy();
  }

  /**
   * Set the proxy information.
   */
  protected void setProxy() {
    if ( System.getenv( JAVA_OPTS_ENV ) != null ) {
      String[] opts = System.getenv( JAVA_OPTS_ENV ).split( " " );

      for ( String opt : opts ) {
        String[] pair = opt.split( "=" );
        if ( pair.length > 1 ) {
          String key = pair[0].replace( "-D", "" );
          String value = pair[1];
          if ( key.contains( "http.proxy" ) ) { // set only proxy properties
            System.setProperty( key, value );
          }
        }
      }
    }

    long memory = Runtime.getRuntime().maxMemory() / ( 1024 * 1024 );
    LOG.info( "Max memory (mb): " + memory );
    LOG.info( "Proxy set: " + System.getProperty( "http.proxySet" ) );
    LOG.info( "Proxy host: " + System.getProperty( "http.proxyHost" ) );
    LOG.info( "Proxy port: " + System.getProperty( "http.proxyPort" ) );
    LOG.info( "Non-proxy hosts: " + System.getProperty( "http.nonProxyHosts" ) );
  }

  @Override
  public void stop() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
        ControllerFactory.class );
    QueryController qc = context.getBean( QueryController.class );
    qc.stop();
    super.stop();
  }
}
