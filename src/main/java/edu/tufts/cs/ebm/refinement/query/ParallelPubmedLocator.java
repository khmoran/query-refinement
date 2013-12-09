package edu.tufts.cs.ebm.refinement.query;

import java.rmi.RemoteException;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.review.systematic.SystematicReview;
import edu.tufts.cs.ebm.util.Util;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleSetChoiceE;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleType;

/**
 * Thread to query PubMed.
 */
public class ParallelPubmedLocator extends PubmedService implements Runnable {
  /** The logger. */
  private static final Log LOG = LogFactory
      .getLog( ParallelPubmedLocator.class );
  /** The PubmedId to look up. */
  protected PubmedId pmid;
  /** The citation found. */
  protected Citation citation;
  /** The review. */
  protected SystematicReview activeReview;

  /**
   * Default constructor.
   * 
   * @throws AxisFault
   */
  public ParallelPubmedLocator( PubmedId pmid ) throws AxisFault {
    init();
    this.pmid = pmid;
  }

  /**
   * Default constructor.
   * 
   * @throws AxisFault
   */
  public ParallelPubmedLocator( SystematicReview activeReview, PubmedId pmid )
      throws AxisFault {
    init();
    this.activeReview = activeReview;
    this.pmid = pmid;
  }

  /**
   * Get the citation from the PubmedId.
   * 
   * @param pmid
   */
  public void locate() {
    Citation c = fetch( pmid );
    for ( int attempt = 1; c == null && attempt <= DEFAULT_TRIES; attempt++ ) {
      LOG.warn( "Error fetching citation " + pmid + " on attempt " + attempt
          + "; trying again." );

      try { // sleep for 15 minutes to prevent rate limiting
        Thread.sleep( DEFAULT_WAIT_MS );
        init(); // reinitialize the service before trying again
      } catch ( InterruptedException | AxisFault e ) {
        LOG.error( e );
      }

      c = fetch( pmid );
    }

    if ( c != null ) {
      this.citation = c;

      setChanged();
      notifyObservers( c );
    } else {
      if ( activeReview != null ) {
        MainController.EM.getTransaction().begin();
        this.activeReview.addBlacklisted( pmid );
        MainController.EM.persist( this.activeReview );
        MainController.EM.getTransaction().commit();
        LOG.warn( "Null citation: " + pmid + "; adding to blacklist ("
            + activeReview.getBlacklist().size() + " total)" );
      } else {
        LOG.warn( "Null citation: " + pmid );
      }
    }
  }

  @Override
  protected Citation fetch( PubmedId pmid ) {
    Citation c = Util.getCitation( pmid );

    if ( c != null ) {
      return c;
    }

    EFetchPubmedServiceStub.EFetchRequest req = new EFetchPubmedServiceStub.EFetchRequest();
    req.setId( pmid.toString() );
    EFetchPubmedServiceStub.EFetchResult res;

    try {
      res = eFetchService.run_eFetch( req );

      if ( res == null || res.getPubmedArticleSet() == null )
        return null;

      for ( int j = 0; j < res.getPubmedArticleSet()
          .getPubmedArticleSetChoice().length; j++ ) {
        PubmedArticleSetChoiceE[] articleSet = res.getPubmedArticleSet()
            .getPubmedArticleSetChoice();
        PubmedArticleType art = articleSet[j].getPubmedArticle();
        if ( art != null ) {
          c = articleToCitation( art );
          pmid.setTitle( c.getTitle() );
        }
      }
    } catch ( RemoteException e ) { // try one more time
      LOG.error( e );
      return null;
    }

    // try { // add the result to the cache
    // defaultCache.put( pmid, c );
    // } catch ( CacheException e ) {
    // LOG.error( e );
    // }

    return c;
  }

  /**
   * Get the citation.
   * 
   * @return
   */
  public Citation getCitation() {
    return this.citation;
  }

  @Override
  public void run() {
    locate();
  }
}
