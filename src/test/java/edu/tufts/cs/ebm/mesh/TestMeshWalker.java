package edu.tufts.cs.ebm.mesh;

import org.testng.annotations.Test;

import com.scireum.open.xml.StructuredNode;

/**
 * Test the MeshWalker class.
 */
public class TestMeshWalker {

  /**
   * Test loading a MeSH term.
   */
  @Test
  public void testLoadMeshTerm() {
    MeshWalker walker = new MeshWalker();

    long loadStart = System.currentTimeMillis();
    StructuredNode r = walker.getTerm( "Platelet Aggregation Inhibitors" );
    long loadEnd = System.currentTimeMillis();

    double loadDuration = ( (double) loadEnd - (double) loadStart )
        / (double) 1000;
    System.out.println( "Load duration: " + loadDuration + "sec" );

    assert r != null;
  }
}
