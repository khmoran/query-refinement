package edu.tufts.cs.ebm.review.systematic;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javafx.collections.ObservableSet;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import com.avaje.ebean.validation.Length;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

import edu.tufts.cs.ebm.util.PubmedDate;

@Entity
public class Citation implements Comparable<Citation>, Serializable {
  /** Default generated serial version UID. */
  private static final long serialVersionUID = -9160999287566221525L;
  /** The number of decimal places to round to. */
  public static final int ROUND_TO = 4;
  /** The callback identifier for the pmid property. */
  public static final String PMID_ID = "pmid";
  /** The callback identifier for the title property. */
  public static final String TITLE_ID = "title";
  /** The callback identifier for the abstract property. */
  public static final String ABSTRACT_ID = "abstract";
  /** The callback identifier for the authors property. */
  public static final String AUTHORS_ID = "authors";
  /** The callback identifier for the journal property. */
  public static final String JOURNAL_ID = "journal";
  /** The callback identifier for the MeSH terms property. */
  public static final String MESH_TERMS_ID = "meshTerms";
  /** The callback identifier for the date completed property. */
  public static final String DATE_ID = "date";
  /** The simple id. */
  @Id
  protected long id;
  /** The PubMed id. */
  @OneToOne
  protected PubmedId pmid;
  /** The title. */
  @Length( max = 5000 )
  protected String title;
  /** The abstract. */
  @Length( max = 100000 )
  protected String abstr;
  /** The date completed. */
  @Transient
  protected PubmedDate date;
  /** The authors. */
  @Length( max = 5000 )
  protected String authors;
  /** The journal. */
  @Length( max = 5000 )
  protected String journal;
  /** The MeSH terms. */
  @Length( max = 5000 )
  protected String meshStr;
  /** The MeSH terms. */
  @Transient
  protected Set<String> meshTerms = new HashSet<>();

  /**
   * Get the simple id.
   * @return
   */
  public long getId() {
    return this.pmid.longValue();
  }

  /**
   * Set the simple id.
   * @param id
   */
  public void setId( long id ) {
    this.id = id;
  }


  /**
   * Default constructor.
   */
  public Citation() {
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ( ( this.pmid == null ) ? 0 : this.pmid.hashCode() );
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
    Citation other = (Citation) obj;
    if ( this.pmid == null ) {
      if ( other.pmid != null )
        return false;
    } else if ( !this.pmid.equals( other.pmid ) )
      return false;
    return true;
  }

  /**
   * Constructor with PubmedId.
   * @param pmid
   */
  public Citation( PubmedId pmid ) {
    this.pmid = pmid;
    this.id = pmid.longValue();
  }

  /**
   * Complete constructor.
   * @param pmid
   * @param abstr
   * @param dateCompleted
   * @param investigators
   * @param meshTerms
   */
  public Citation( PubmedId pmid, String title, String abstr, String journal,
      PubmedDate dateCompleted, String authors,
      ObservableSet<String> meshTerms ) {
    setPmid( pmid );
    setTitle( title );
    setAbstr( abstr );
    setDate( dateCompleted );
    setJournal( journal );
    setAuthors( authors );
    setMeshTerms( meshTerms );
  }

  /**
   * Get the journal.
   * @return
   */
  public String getJournal() {
    return this.journal;
  }

  /**
   * Set the journal.
   * @param journal
   */
  public void setJournal( String journal ) {
    this.journal = journal;
  }

  /** Get the PubMed id. */
  public PubmedId getPmid() {
    return this.pmid;
  }

  /**
   * Set the PubMed id.
   * @param pmid
   */
  public void setPmid( PubmedId pmid ) {
    this.pmid = pmid;
    this.id = pmid.longValue();
  }

  /**
   * Get the abstract.
   * @return
   */
  public String getAbstr() {
    return this.abstr;
  }

  /** Set the abstract.
   * @param abstr
   */
  public void setAbstr( String abstr ) {
    this.abstr = abstr;
  }

  /**
   * Get the data completed.
   * @return
   */
  public PubmedDate getDate() {
    return this.date;
  }

  /**
   * Set the date completed.
   * @param dateCompleted
   */
  public void setDate( PubmedDate date ) {
    this.date = date;
  }

  /**
   * Get the investigators.
   * @return
   */
  public String getAuthors() {
    return this.authors;
  }

  /**
   * Set the authors.
   * @param authors
   */
  public void setAuthors( String authors ) {
    this.authors = authors;
  }

  /**
   * Get the MeSH terms.
   * @return
   */
  public Set<String> getMeshTerms() {
    return this.meshTerms;
  }

  /**
   * Get the MeSH string.
   * @return
   */
  public String getMeshStr() {
    return this.meshStr;
  }

  /**
   * Set the MeSH terms.
   * @param terms
   */
  public void setMeshTerms( ObservableSet<String> terms ) {
    this.meshTerms = terms;
    Joiner joiner = Joiner.on( ',' ).skipNulls();
    this.meshStr = joiner.join( terms );
  }

  /**
   * Set the MeSH string.
   * @param mesh
   */
  public void setMeshStr( String mesh ) {
    this.meshStr = mesh;
    this.meshTerms = Sets.newHashSet( Splitter.on( ',' )
      .trimResults()
      .omitEmptyStrings()
      .split( mesh ) );
  }

  /**
   * Get the title.
   * @return
   */
  public String getTitle() {
    return this.title;
  }

  /** Set the title.
   * @param abstr
   */
  public void setTitle( String abstr ) {
    this.title = abstr;
  }

  @Override
  public int compareTo( Citation o ) {
    return this.pmid.compareTo( o.getPmid() );
  }

  @Override
  public String toString() {
    if ( this.title != null && this.title != null &&
         this.title.isEmpty() ) {
      return this.title + " [" + this.pmid + "]";
    } else {
      return this.pmid.toString();
    }
  }
}
