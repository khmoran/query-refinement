package edu.tufts.cs.ebm.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PubmedDate extends Date {
  /** Default generated serial version UID. */
  private static final long serialVersionUID = -4153534473465627506L;
  /** The date format to use. */
  public static final DateFormat FORMAT = new SimpleDateFormat( "yyyy" );

  /**
   * Default constructor.
   */
  public PubmedDate() {
    super();
  }

  /**
   * Copy constructor.
   * 
   * @param d
   */
  public PubmedDate( Date d ) {
    super( d.getTime() );
  }

  @Override
  public String toString() {
    return FORMAT.format( this );
  }
}
