package edu.tufts.cs.ebm.review.systematic.simulation.drivers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.review.systematic.simulation.Simulator;
import edu.tufts.cs.ml.exception.CommandLineArgumentException;

public class SimulateReview {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( SimulateReview.class );

  /**
   * @param args
   * @throws CommandLineArgumentException
   */
  public static void main( String[] args ) throws CommandLineArgumentException {
    SimulateReviewArguments cmd = new SimulateReviewArguments( args );
    printInfo( cmd );

    String dataset = cmd.getDataset();
    boolean online = cmd.isOnline();
    String representation = cmd.getRepresentation();
    String classifier = cmd.getClassifier();
    String onlineStr = ( online ) ? "Online" : "Offline";
    String pkgName = SimulateReview.class.getPackage().getName().replace(
        "drivers", "" ).concat( onlineStr.toLowerCase() ) + ".";
    String className = pkgName + onlineStr + "Simulator" + representation + classifier;
    
    LOG.info( "Attempting to load simulation " + className + " on dataset " + dataset + "..." );
    try {
      Class<?> simulator = SimulateReview.class.getClassLoader().loadClass( className );
      Constructor<?> constructor = simulator.getConstructor( String.class );
      Object instance = constructor.newInstance( dataset );
      if ( instance instanceof Simulator ) {
        Simulator s = (Simulator) instance;
        s.simulateReview();
      }
    } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException
            | InstantiationException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e ) {
      LOG.error( "Simulator " + className + " does not exist or could not be " +
        "instantiated.", e );
    } catch ( Exception e ) {
      LOG.error( e );
    }
  }


  /**
   * Print the configuration to the console.
   * @param cmd
   */
  protected static void printInfo( SimulateReviewArguments cmd ) {
    LOG.info( "Running Simulate Review with: " +
      "\n\tDataset:\t" + cmd.getDataset() +
      "\n\tOnline:\t" + cmd.isOnline() +
      "\n\tRepresentation:\t" + cmd.getRepresentation() +
      "\n\tClassifier:\t" + cmd.getClassifier() );
  }
}
