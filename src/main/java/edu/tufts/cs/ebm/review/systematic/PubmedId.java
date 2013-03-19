package edu.tufts.cs.ebm.review.systematic;

import java.text.NumberFormat;
import java.text.ParseException;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class PubmedId extends Number implements Comparable<PubmedId> {
  /** The PubMed id number format. */
  protected static final NumberFormat PMID_FORMAT = NumberFormat.getInstance();
  /** Default generated serial version UID. */
  private static final long serialVersionUID = -8136962779230818999L;
  /** The internal value. */
  @Id
  protected Long value = (long) 0;
  /** The title associated with the id. */
  protected String title;

  /**
   * Get the title.
   * @return
   */
  public String getTitle() {
    return this.title;
  }

  /**
   * Set the title.
   * @param title
   */
  public void setTitle( String title ) {
    this.title = title;
  }

  /**
   * Default constructor.
   */
  public PubmedId() {
    super();
    init();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ( ( this.value == null ) ? 0 : this.value.hashCode() );
    return result;
  }

  @Override
  public boolean equals( Object obj ) {
    if ( this == obj )
      return true;
    if ( obj == null )
      return false;
    if ( !( obj instanceof PubmedId ) )
      return false;
    PubmedId other = (PubmedId) obj;
    if ( this.value == null ) {
      if ( other.value != null )
        return false;
    } else if ( !this.value.equals( other.value ) )
      return false;
    return true;
  }

  /**
   * Constructor that takes a long.
   * @param value
   */
  public PubmedId( long value ) throws NumberFormatException {
    super();
    init();

    set( value );
  }

  /**
   * Constructor that takes a String.
   * @param value
   * @throws ParseException
   */
  public PubmedId( String value ) throws NumberFormatException, ParseException {
    super();
    init();

    Long l = PMID_FORMAT.parse( value ).longValue();

    set( l );

    this.value = l;
  }

  /**
   * Set the value.
   * @param value
   */
  public void setValue( Long value ) {
    set( value );
  }

  /**
   * Set the value.
   * @param value
   * @throws ParseException
   */
  public void setValue( String value ) throws ParseException {
    Long l = PMID_FORMAT.parse( value ).longValue();
    set( l );
  }

  /**
   * Get the value.
   * @return
   */
  public Long getValue() {
    return this.value;
  }

  /**
   * Set the value.
   * @param value
   */
  protected void set( long value ) {
    if ( !( value >= 0 && value <= 99999999 ) ) {
      throw new NumberFormatException(
          "Does not conform to PubMed id format: " + value );
    }

    this.value = value;
  }

  /**
   * Initialize the format.
   */
  protected static void init() {
    PMID_FORMAT.setMinimumIntegerDigits( 8 );
    PMID_FORMAT.setMaximumIntegerDigits( 8 );
    PMID_FORMAT.setMinimumFractionDigits( 0 );
    PMID_FORMAT.setMaximumFractionDigits( 0 );
    PMID_FORMAT.setGroupingUsed( false );
  }

  @Override
  public int intValue() {
    return value.intValue();
  }

  @Override
  public long longValue() {
    return value;
  }

  @Override
  public float floatValue() {
    return value.floatValue();
  }

  @Override
  public double doubleValue() {
    return value.doubleValue();
  }

  @Override
  public String toString() {
    return PMID_FORMAT.format( value );
  }

  @Override
  public int compareTo( PubmedId o ) {
    if ( value.longValue() < o.getValue().longValue() ) {
      return -1;
    } else if ( value.longValue() > o.getValue().longValue() ) {
      return 1;
    } else {
      return 0;
    }
  }

}
