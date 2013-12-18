package edu.tufts.cs.ebm.refinement.query;

import edu.tufts.cs.ebm.refinement.query.controller.MainController;
import edu.tufts.cs.ebm.review.systematic.Citation;
import edu.tufts.cs.ebm.review.systematic.PubmedId;
import edu.tufts.cs.ebm.util.Util;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.AbstractTextType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.AbstractType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.ArticleDateType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.ArticleType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.AuthorType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.AuthorTypeSequence_type0;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.DateCompletedType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.DateCreatedType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.DateRevisedType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.JournalType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.MedlineCitationType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.MeshHeadingListType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.MeshHeadingType;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleSetChoiceE;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleType;
import gov.nih.nlm.ncbi.www.soap.eutils.EUtilsServiceStub;

import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

/**
 * Thread to query PubMed.
 */
public abstract class PubmedService extends Observable {
  /** The logger. */
  private static final Log LOG = LogFactory.getLog( PubmedService.class );
  /** The EUtils service. */
  protected EUtilsServiceStub eUtilsService;
  /** The Pubmed service. */
  protected EFetchPubmedServiceStub eFetchService;
  /** The amount to wait between requests. */
  protected static final int DEFAULT_WAIT_MS = 10000;
  /** The number of attempts to fetch a citation. */
  protected static final int DEFAULT_TRIES = 3;
  /** The cache name. */
  protected static final String DEFAULT_CACHE_NAME = "default";
  /** The data cache. */
  protected static JCS defaultCache;

  /**
   * Initialize the services.
   * 
   * @throws AxisFault
   */
  protected void init() throws AxisFault {
    eUtilsService = new EUtilsServiceStub();
    eFetchService = new EFetchPubmedServiceStub();

    try {
      defaultCache = JCS.getInstance( DEFAULT_CACHE_NAME );
      // this.defaultCache.clear();
    } catch ( CacheException e ) {
      LOG.error( "Error intitializing prefetching cache.", e );
      e.printStackTrace();
    }
  }

  /**
   * Convert the articles to citations.
   * 
   * @param articles
   * @return
   */
  protected Citation articleToCitation( PubmedArticleType articleType ) {
    MedlineCitationType mct = articleType.getMedlineCitation();
    String seedStr = mct.getPMID().getString();
    PubmedId id = Util.createOrUpdatePmid( Long.valueOf( seedStr ) );

    Citation c = Util.getCitation( id );

    if ( c != null ) {
      return c;
    }

    if ( mct.getArticle() != null ) {
      Date date = getDate( mct );
      String title = getTitle( mct );
      String authors = getAuthors( mct );
      String journal = getJournal( mct );
      String abstr = getAbstract( mct );
      ObservableSet<String> meshSet = getMeshTerms( mct );

      if ( title != null && !title.isEmpty() ) {
        id.setTitle( title );
        MainController.EM.getTransaction().begin();
        c = new Citation( id, title, abstr, journal, date, authors, meshSet );
        MainController.EM.persist( c );
        MainController.EM.getTransaction().commit();
      }
    }

    return c;
  }

  /**
   * Fetch the citations.
   * 
   * @param req2
   * @return
   * @throws RemoteException
   */
  protected Collection<Citation> fetch(
      EFetchPubmedServiceStub.EFetchRequest req2 ) {
    Collection<Citation> c = new HashSet<>();
    try {
      EFetchPubmedServiceStub.EFetchResult res2 = eFetchService
          .run_eFetch( req2 );
      for ( int j = 0; j < res2.getPubmedArticleSet()
          .getPubmedArticleSetChoice().length; j++ ) {
        PubmedArticleSetChoiceE[] articleSet = res2.getPubmedArticleSet()
            .getPubmedArticleSetChoice();
        PubmedArticleType art = articleSet[j].getPubmedArticle();
        if ( art != null ) {
          LOG.trace( "found " + art.getMedlineCitation().getPMID() + ": "
              + art.getMedlineCitation().getArticle().getArticleTitle() );
          Citation citation = articleToCitation( art );

          if ( citation != null ) {
            c.add( citation );
          }
        }
      }
    } catch ( RemoteException e ) {
      LOG.error( e );
      return null;
    }

    return c;
  }

  /**
   * Fetch the Citations for the given PubmedId.
   * 
   * @param pmid
   * @return
   */
  protected Set<Citation> fetch( Set<PubmedId> pmids ) {
    Set<Citation> results = new HashSet<>();
    EFetchPubmedServiceStub.EFetchRequest req = new EFetchPubmedServiceStub.EFetchRequest();

    String pmidStr = "";
    for ( PubmedId pmid : pmids ) {
      Citation c = Util.getCitation( pmid );
      if ( c != null ) {
        results.add( c );
      } else {
        pmidStr += pmid + ",";
      }
    }
    pmidStr = pmidStr.substring( 0, pmidStr.length() - 2 );

    req.setId( pmidStr );
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
          LOG.trace( "found " + art.getMedlineCitation().getPMID() + ": "
              + art.getMedlineCitation().getArticle().getArticleTitle() );
          Citation c = articleToCitation( art );
          results.add( c );
        }
      }
    } catch ( RemoteException e ) { // try one more time
      LOG.error( e );
      return null;
    }

    return results;
  }

  /**
   * Fetch the Citation for the given PubmedId.
   * 
   * @param pmid
   * @return
   */
  protected Citation fetch( PubmedId pmid ) {
    // try to find it in the cache first
    // Citation c = (Citation) defaultCache.get( pmid );
    // if ( c != null ) {
    // LOG.debug( "Found " + pmid + " in cache!" );
    // return c;
    // }
    Citation c = Util.getCitation( pmid );

    if ( c != null ) {
      return c;
    }

    EFetchPubmedServiceStub.EFetchRequest req = new EFetchPubmedServiceStub.EFetchRequest();
    req.setId( pmid.toString() );
    EFetchPubmedServiceStub.EFetchResult res;

    try {
      res = eFetchService.run_eFetch( req );

      if ( res == null || res.getPubmedArticleSet() == null ) {
        return null;
      }

      for ( int j = 0; j < res.getPubmedArticleSet()
          .getPubmedArticleSetChoice().length; j++ ) {
        PubmedArticleSetChoiceE[] articleSet = res.getPubmedArticleSet()
            .getPubmedArticleSetChoice();
        PubmedArticleType art = articleSet[j].getPubmedArticle();
        if ( art != null ) {
          LOG.trace( "found " + art.getMedlineCitation().getPMID() + ": "
              + art.getMedlineCitation().getArticle().getArticleTitle() );
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
   * Get the abstract from the citation.
   * 
   * @param mct
   * @return
   */
  protected String getAbstract( MedlineCitationType mct ) {
    String abstr = "";

    ArticleType article = mct.getArticle();
    if ( mct.getArticle().getAbstract() != null ) {
      AbstractType at = article.getAbstract();
      if ( at != null ) {
        AbstractTextType[] abtexts = at.getAbstractText();
        if ( abtexts != null ) {
          for ( AbstractTextType ab : abtexts ) {
            abstr += ab.getString();
          }
        }
      }
    }

    return abstr;
  }

  /**
   * Get the authors from the citation.
   * 
   * @param mct
   * @return
   */
  protected String getAuthors( MedlineCitationType mct ) {
    String authors = "";

    ArticleType article = mct.getArticle();
    if ( article.getAuthorList() != null
        && article.getAuthorList().getAuthor() != null ) {
      for ( AuthorType at : article.getAuthorList().getAuthor() ) {
        AuthorTypeSequence_type0 ats = at.getAuthorTypeChoice_type0()
            .getAuthorTypeSequence_type0();
        if ( ats != null && ats.getLastName() != null ) {
          authors += ats.getLastName();
          if ( ats.getForeName() != null ) {
            authors += ", " + ats.getForeName();
          }
          authors += "; ";
        }
      }
    }
    if ( authors.length() > 2 ) {
      authors = authors.substring( 0, authors.length() - 2 );
    }

    if ( authors.isEmpty() ) {
      authors = "[No authors listed]";
    }

    return authors;
  }

  /**
   * Get the date from the citation.
   * 
   * @param mct
   * @return
   */
  protected Date getDate( MedlineCitationType mct ) {
    Calendar cal = null;

    try {
      ArticleType article = mct.getArticle();
      if ( article != null && article.getArticleDate() != null ) {
        for ( ArticleDateType d : article.getArticleDate() ) {
          cal = new GregorianCalendar( Integer.parseInt( d.getYear() ),
              Integer.parseInt( d.getMonth() ), Integer.parseInt( d.getDay() ) );
        }
      }
      if ( cal == null && mct.getDateCompleted() != null ) {
        DateCompletedType d = mct.getDateCompleted();
        cal = new GregorianCalendar( Integer.parseInt( d.getYear() ),
            Integer.parseInt( d.getMonth() ), Integer.parseInt( d.getDay() ) );
      } else if ( cal == null && mct.getDateCreated() != null ) {
        DateCreatedType d = mct.getDateCreated();
        cal = new GregorianCalendar( Integer.parseInt( d.getYear() ),
            Integer.parseInt( d.getMonth() ), Integer.parseInt( d.getDay() ) );
      } else if ( cal == null && mct.getDateRevised() != null ) {
        DateRevisedType d = mct.getDateRevised();
        cal = new GregorianCalendar( Integer.parseInt( d.getYear() ),
            Integer.parseInt( d.getMonth() ), Integer.parseInt( d.getDay() ) );

      }
    } catch ( NumberFormatException e ) {
      LOG.error( "Could not parse date." );
    }

    Date date = null;
    if ( cal != null ) {
      date = cal.getTime();
    }

    return date;
  }

  /**
   * Get the journal from the citation.
   * 
   * @param mct
   * @return
   */
  protected String getJournal( MedlineCitationType mct ) {
    String journal = "";

    ArticleType article = mct.getArticle();
    if ( mct.getArticle().getAbstract() != null ) {
      JournalType at = article.getJournal();
      if ( at != null ) {
        journal = at.getTitle();
      }
    }

    return journal;
  }

  /**
   * Get the MeSH terms from the citation.
   * 
   * @param mct
   * @return
   */
  protected ObservableSet<String> getMeshTerms( MedlineCitationType mct ) {
    ObservableSet<String> meshSet = FXCollections.observableSet();

    MeshHeadingListType mhlt = mct.getMeshHeadingList();
    if ( mhlt != null ) {
      for ( MeshHeadingType mht : mhlt.getMeshHeading() ) {
        String term = mht.getDescriptorName().getString();
        meshSet.add( term );
      }
    }

    return meshSet;
  }

  /**
   * Get the title from the article.
   * 
   * @param article
   * @return
   */
  protected String getTitle( MedlineCitationType mct ) {
    String title = "";

    ArticleType article = mct.getArticle();
    if ( article.getArticleTitle() != null ) {
      title = article.getArticleTitle().getString();

      if ( title.startsWith( "[" ) && title.endsWith( "]." ) ) {
        title = title.substring( 1, title.length() - 2 ) + ".";
      }
    }

    return title;
  }
}
