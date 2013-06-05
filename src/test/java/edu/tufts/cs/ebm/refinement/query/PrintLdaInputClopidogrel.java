package edu.tufts.cs.ebm.refinement.query;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;

import org.apache.axis2.AxisFault;
import org.testng.annotations.Test;

import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.SimulateReview;

public class PrintLdaInputClopidogrel extends SimulateReview {
  /** The name for the output file. */
  protected static final String OUTPUT_FILE = "docs_en.csv";

  @Test
  public void printLdaInput() throws AxisFault, FileNotFoundException {
    StringBuffer popQuery = new StringBuffer( activeReview.getQueryP() );
    StringBuffer icQuery = new StringBuffer( activeReview.getQueryIC() );

    // run the initial query
    ParallelPubmedSearcher searcher = new ParallelPubmedSearcher(
        "(" + popQuery + ") AND (" + icQuery + ")",
        activeReview, new HashSet<Citation>() );
    search( searcher );

    FileOutputStream fs = new FileOutputStream( OUTPUT_FILE );
    PrintStream ps = new PrintStream( fs );
    
    for ( Citation c : searcher.getCitations() ) {
      ps.append( c.getPmid() + "\tX\t\"" + c.getTitle() + " " + c.getAbstr() + "\"\n" );
    }
    
    ps.close();
  }
  

  @Override
  public void simulateReview()
    throws InterruptedException, IOException {
    // do nothing
  }
}
