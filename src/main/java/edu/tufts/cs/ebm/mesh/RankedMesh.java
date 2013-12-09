package edu.tufts.cs.ebm.mesh;

import edu.tufts.cs.ebm.refinement.query.PicoElement;

public class RankedMesh implements Comparable<Object> {
  /** The MeSH term. */
  protected String term;
  /** The info gain associated with this MeSH term. */
  protected double infoGain;
  /** The positivity of the MeSH term. */
  protected boolean positivity;
  /** The PICO element this term corresponds to. */
  protected PicoElement pico;

  /**
   * Default constructor.
   * 
   * @param term
   * @param infoGain
   * @param positivity
   * @param pico
   */
  public RankedMesh( String term, double infoGain, boolean positivity,
      PicoElement pico ) {
    this.term = term;
    this.infoGain = infoGain;
    this.positivity = positivity;
    this.pico = pico;
  }

  /**
   * The MeSH term.
   * 
   * @return
   */
  public String getTerm() {
    return this.term;
  }

  /**
   * Set the MeSH term.
   * 
   * @param term
   */
  public void setTerm( String term ) {
    this.term = term;
  }

  /**
   * The info gain w.r.t. the active review.
   * 
   * @return
   */
  public double getInfoGain() {
    return this.infoGain;
  }

  /**
   * Set the info gain w.r.t. to active review.
   * 
   * @param infoGain
   */
  public void setInfoGain( double infoGain ) {
    this.infoGain = infoGain;
  }

  /**
   * Whether the term is "positive" w.r.t. the active review.
   * 
   * @return
   */
  public boolean isPositive() {
    return this.positivity;
  }

  /**
   * Set the positivity of the term.
   * 
   * @param positivity
   */
  public void setPositivity( boolean positivity ) {
    this.positivity = positivity;
  }

  /**
   * Get the PICO element.
   * 
   * @return
   */
  public PicoElement getPico() {
    return this.pico;
  }

  /**
   * Set the PICO element.
   * 
   * @param pico
   */
  public void setPico( PicoElement pico ) {
    this.pico = pico;
  }

  @Override
  public int compareTo( Object o ) {
    if ( o instanceof RankedMesh ) {
      RankedMesh rm = (RankedMesh) o;
      int val = Double.compare( infoGain, rm.getInfoGain() );

      if ( val == 0 ) {
        return rm.getTerm().compareTo( term );
      } else {
        return val;
      }
    } else {
      return term.compareTo( o.toString() );
    }
  }

  @Override
  public String toString() {
    String posStr = positivity ? "+" : "-";
    return infoGain + ": " + term + " (" + posStr + ") [" + pico.name() + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ( ( this.term == null ) ? 0 : this.term.hashCode() );
    return result;
  }

  @Override
  public boolean equals( Object obj ) {
    if ( this == obj )
      return true;
    if ( obj == null )
      return false;
    if ( getClass() != obj.getClass() )
      return false;
    RankedMesh other = (RankedMesh) obj;
    if ( this.term == null ) {
      if ( other.term != null )
        return false;
    } else if ( !this.term.equals( other.term ) )
      return false;
    return true;
  }
}
