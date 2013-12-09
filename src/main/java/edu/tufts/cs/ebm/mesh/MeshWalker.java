package edu.tufts.cs.ebm.mesh;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXException;

import com.scireum.open.commons.Value;
import com.scireum.open.xml.NodeHandler;
import com.scireum.open.xml.StructuredNode;
import com.scireum.open.xml.XMLReader;

import edu.tufts.cs.ebm.refinement.Launcher;

public class MeshWalker {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( Launcher.class );
  /** The XML file containing the MeSH tree. */
  protected static final String TREE_XML_FILE = "src/main/resources/desc2013.xml";
  /** The "DescriptorRecord" node name. */
  protected static final String DESCRIPTOR_RECORD_NODE_NAME = "DescriptorRecord";
  /** The "DescriptorName" node name. */
  protected static final String DESCRIPTOR_NAME_NODE_NAME = "DescriptorName";

  /**
   * Default constructor.
   */
  public MeshWalker() {
    // use:
    // Set<StructuredNode> nodes = walker.getTerms( topK );
    //
    // for ( StructuredNode node : nodes ) {
    //
    // }
  }

  /**
   * Default constructor.
   */
  public StructuredNode getTerm( String term ) {
    XMLReader r = new XMLReader();
    TermHandler th = new TermHandler( term );
    r.addHandler( DESCRIPTOR_RECORD_NODE_NAME, th );

    try {
      r.parse( new FileInputStream( TREE_XML_FILE ) );
    } catch ( ParserConfigurationException | SAXException | IOException e ) {
      LOG.error( e );
    }

    Set<StructuredNode> nodes = th.getNodes();

    if ( nodes.size() > 0 ) {
      return nodes.toArray( new StructuredNode[0] )[0];
    } else {
      return null;
    }
  }

  /**
   * Default constructor.
   */
  public Set<StructuredNode> getTerms( Collection<String> terms ) {
    XMLReader r = new XMLReader();
    TermHandler th = new TermHandler( terms );
    r.addHandler( "DescriptorRecord", th );

    try {
      r.parse( new FileInputStream( TREE_XML_FILE ) );
    } catch ( ParserConfigurationException | SAXException | IOException e ) {
      LOG.error( e );
    }

    return th.getNodes();
  }

  public class TermHandler implements NodeHandler {
    /** The terms to find. */
    protected Collection<String> terms;
    /** The terms found. */
    protected Set<StructuredNode> nodes = new HashSet<>();

    /**
     * Default constructor.
     * 
     * @param terms
     */
    public TermHandler( String term ) {
      this.terms = new HashSet<>();
      terms.add( term );
    }

    /**
     * Default constructor.
     * 
     * @param terms
     */
    public TermHandler( Collection<String> terms ) {
      this.terms = terms;
    }

    /**
     * Get the found terms.
     * 
     * @return
     */
    public Set<StructuredNode> getNodes() {
      return this.nodes;
    }

    @Override
    public void process( StructuredNode node ) {
      try {
        Value v = node.queryValue( DESCRIPTOR_NAME_NODE_NAME );
        if ( terms.contains( v.toString() ) ) {
          nodes.add( node );
        }
      } catch ( XPathExpressionException e ) {
        LOG.error( e );
      }
    }
  }
}
