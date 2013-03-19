package edu.tufts.cs.ebm.review.systematic;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;

@Entity
public class SystematicReview implements Serializable,
  Comparable<SystematicReview> {
  /** The Logger for this class. */
  protected static final Log LOG = LogFactory.getLog( MainController.class );
  /** Default generated serial version UID. */
  private static final long serialVersionUID = -5992075362650101039L;
  /** The generated entity id. */
  @Id
  @GeneratedValue( strategy = GenerationType.AUTO )
  protected Long id;
  /** The name of the review. */
  protected String name;
  /** The date the systematic review was created. */
  @Temporal( TemporalType.DATE )
  protected Date createdOn;
  /** The creator of the review. */
  protected String creator;
  /** The most recent population query. */
  protected String queryP;
  /** The most recent intervention/comparison query. */
  protected String queryIC;
  /** The most recent outcome query. */
  protected String queryO;
  /** The PMIDs for the seed articles. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_seeds" )
  protected Set<PubmedId> seeds = new HashSet<>();
  /** The PMIDs for relevant articles. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_relevant_l1" )
  protected Set<PubmedId> relevantLevel1 = new HashSet<>();
  /** The PMIDs for the level 2 articles. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_relevant_l2" )
  protected Set<PubmedId> relevantLevel2 = new HashSet<>();
  /** The PMIDs for articles with irrelevant populations. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_irrel_pop" )
  protected Set<PubmedId> irrelevantP = new HashSet<>();
  /** The PMIDs for articles with irrelevant interventions/comparisons. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_irrel_interv_comp" )
  protected Set<PubmedId> irrelevantIC = new HashSet<>();
  /** The PMIDs for articles with irrelevant outcomes. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_irrel_outc" )
  protected Set<PubmedId> irrelevantO = new HashSet<>();
  /** Blacklisted PMIDs. */
  @ManyToMany( cascade = CascadeType.ALL )
  @JoinTable( name = "review_blacklist" )
  protected Set<PubmedId> blacklist = new HashSet<>();

  /** The seed citations. */
  @Transient
  protected Set<Citation> seedCitations = new HashSet<>();

  /**
   * Get the seed citations.
   * @return
   */
  public Set<Citation> getSeedCitations() {
    return this.seedCitations;
  }

  /**
   * Set the seed citations.
   * @param seedCitations
   */
  public void setSeedCitations( Set<Citation> seedCitations ) {
    this.seedCitations = seedCitations;
  }

  /**
   * Default constructor.
   */
  public SystematicReview() {
    this.createdOn = new Date();
  }

  /**
   * Get the last population query executed for this review.
   * @return
   */
  public String getQueryP() {
    return this.queryP;
  }

  /**
   * Set the last population query executed for this review.
   * @param queryP
   */
  public void setQueryP( String queryP ) {
    this.queryP = queryP;
  }

  /**
   * Get the last intervention/comparison query executed for this review.
   * @return
   */
  public String getQueryIC() {
    return this.queryIC;
  }

  /**
   * Set the last intervention/comparison query executed for this review.
   * @param queryIC
   */
  public void setQueryIC( String queryIC ) {
    this.queryIC = queryIC;
  }

  /**
   * Get the last outcome query executed for this review.
   * @return
   */
  public String getQueryO() {
    return this.queryO;
  }

  /**
   * Set the last outcome query executed for this review.
   * @param queryO
   */
  public void setQueryO( String queryO ) {
    this.queryO = queryO;
  }

  /**
   * Get the id.
   * @return
   */
  public Long getId() {
    return this.id;
  }

  /**
   * Set the id.
   * @return
   */
  public void setId( Long id ) {
    this.id = id;
  }

  /**
   * Get the name.
   * @return
   */
  public String getName() {
    return this.name;
  }

  /**
   * Set the name.
   * @param name
   */
  public void setName( String name ) {
    this.name = name;
  }

  /**
   * Get the date created.
   * @return
   */
  public Date getCreatedOn() {
    return this.createdOn;
  }

  /**
   * Set the date created.
   * @return
   */
  public void setCreatedOn( Date createdOn ) {
    this.createdOn = createdOn;
  }

  /**
   * Get the creator.
   * @return
   */
  public String getCreator() {
    return this.creator;
  }

  /**
   * Set the creator.
   * @return
   */
  public void setCreator( String creator ) {
    this.creator = creator;
  }

  /**
   * Get the seed PMIDs.
   * @return
   */
  public Set<PubmedId> getSeeds() {
    return this.seeds;
  }

  /**
   * Set the seed PMIDs.
   * @return
   */
  public void setSeeds( Set<PubmedId> seedPmids ) {
    this.seeds = seedPmids;
  }

  /**
   * Get the relevant articles.
   * @return
   */
  public Set<PubmedId> getRelevantLevel1() {
    return this.relevantLevel1;
  }

  /**
   * Set the relevant articles.
   * @return
   */
  public void setRelevantLevel1( Set<PubmedId> relevantLevel1 ) {
    this.relevantLevel1 = relevantLevel1;
  }


  /**
   * Get the relevant articles.
   * @return
   */
  public Set<PubmedId> getRelevantLevel2() {
    return this.relevantLevel2;
  }

  /**
   * Set the relevant articles.
   * @return
   */
  public void setRelevantLevel2( Set<PubmedId> relevantLevel2 ) {
    this.relevantLevel2 = relevantLevel2;
  }

  /**
   * Get the articles with irrelevant populations.
   * @return
   */
  public Set<PubmedId> getIrrelevantP() {
    return this.irrelevantP;
  }

  /**
   * Set the articles with irrelevant populations.
   * @return
   */
  public void setIrrelevantP( Set<PubmedId> irrelevantPopulations ) {
    this.irrelevantP = irrelevantPopulations;
  }

  /**
   * Get the articles with irrelevant interventions.
   * @return
   */
  public Set<PubmedId> getIrrelevantIC() {
    return this.irrelevantIC;
  }

  /**
   * Set the articles with irrelevant interventions.
   * @return
   */
  public void setIrrelevantIC(
      Set<PubmedId> irrelevantIntervention ) {
    this.irrelevantIC = irrelevantIntervention;
  }

  /**
   * Get the articles with irrelevant outcomes.
   * @return
   */
  public Set<PubmedId> getIrrelevantO() {
    return this.irrelevantO;
  }

  /**
   * Set the articles with irrelevant outcomes.
   * @return
   */
  public void setIrrelevantO( Set<PubmedId> irrelevantOutcome ) {
    this.irrelevantO = irrelevantOutcome;
  }

  /**
   * Add a relevant article.
   * @param id
   */
  public void addRelevantLevel1( PubmedId id ) {
    this.relevantLevel1.add( id );
  }

  /**
   * Add a relevant article.
   * @param id
   */
  public void addRelevantLevel2( PubmedId id ) {
    this.relevantLevel2.add( id );
  }

  /**
   * Add an irrelevant article.
   * @param id
   */
  public void addIrrelevantP( PubmedId id ) {
    this.irrelevantP.add( id );
  }

  /**
   * Add an irrelevant article.
   * @param id
   */
  public void addIrrelevantIC( PubmedId id ) {
    this.irrelevantIC.add( id );
  }

  /**
   * Add an irrelevant article.
   * @param id
   */
  public void addIrrelevantO( PubmedId id ) {
    this.irrelevantO.add( id );
  }

  /**
   * Add an irrelevant article.
   * @param id
   */
  public void addIrrelevantAll( PubmedId id ) {
    this.irrelevantP.add( id );
    this.irrelevantIC.add( id );
    this.irrelevantO.add( id );
  }

  /**
   * Get the blacklist.
   * @return
   */
  public Set<PubmedId> getBlacklist() {
    return this.blacklist;
  }

  /**
   * Set the blacklist.
   * @param blacklist
   */
  public void setBlacklist( Set<PubmedId> blacklist ) {
    this.blacklist = blacklist;
  }

  /**
   * Add to the blacklist.
   * @param id
   */
  public void addBlacklisted( PubmedId id ) {
    this.blacklist.add( id );
  }

  @Override
  public int compareTo( SystematicReview o ) {
    return Long.compare( this.getId(), o.getId() );
  }
}
