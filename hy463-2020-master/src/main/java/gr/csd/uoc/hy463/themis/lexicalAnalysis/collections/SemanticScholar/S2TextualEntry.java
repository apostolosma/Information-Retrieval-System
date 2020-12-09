/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.utils.Pair;

import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that holds all textual information read from an entry of the collection
 * It is used by the S2JSONEntryReader
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class S2TextualEntry {

    private static final Logger __LOGGER__ = LogManager.getLogger(S2TextualEntry.class);
    private String __ID__ = null;
    private String __TITLE__ = null;
    private String __ABSTRACT__ = null;
    private List<String> __ENTITIES__ = null;
    private List<String> __FIELDS_OF_STUDY__ = null;
    private List<Pair<String, List<String>>> __AUTHORS__ = null;
    private int __YEAR__ = 0;
    private String __VENUE__ = null;
    private String __JOURNAL_NAME__ = null;
    private List<String> __SOURCES__ = null;
    private List<String> __CITATIONS__ = null;
    
    
    private HashMap<String,Boolean> __DOC_REL_MAP__ = null;
    private List<Pair<String,Boolean>> __LIST_DOC_REL_MAP__=null;
    private double __FREQUENCY__ = 0;
    private Long __QUERY_ID__ = null;
    String __QUERY__ = null;

    public String getId() {
        return __ID__;
    }

    public void setId(String id) {
        this.__ID__ = id;
    }

    public String getTitle() {
        return __TITLE__;
    }

    public void setTitle(String title) {
        this.__TITLE__ = title;
    }

    public String getPaperAbstract() {
        return __ABSTRACT__;
    }

    public void setPaperAbstract(String paperAbstract) {
        this.__ABSTRACT__ = paperAbstract;
    }

    public List<String> getEntities() {
        return __ENTITIES__;
    }

    public void setEntities(List<String> entities) {
        this.__ENTITIES__ = entities;
    }

    public List<String> getFieldsOfStudy() {
        return __FIELDS_OF_STUDY__;
    }

    public void setFieldsOfStudy(List<String> fieldsOfStudy) {
        this.__FIELDS_OF_STUDY__ = fieldsOfStudy;
    }

    public List<Pair<String, List<String>>> getAuthors() {
        return __AUTHORS__;
    }

    public void setAuthors(List<Pair<String, List<String>>> authors) {
        this.__AUTHORS__ = authors;
    }

    public int getYear() {
        return __YEAR__;
    }

    public void setYear(int year) {
        this.__YEAR__ = year;
    }

    public String getVenue() {
        return __VENUE__;
    }

    public void setVenue(String venue) {
        this.__VENUE__ = venue;
    }

    public String getJournalName() {
        return __JOURNAL_NAME__;
    }

    public void setJournalName(String journalName) {
        this.__JOURNAL_NAME__ = journalName;
    }

    public List<String> getSources() {
        return __SOURCES__;
    }

    public void setSources(List<String> sources) {
        this.__SOURCES__ = sources;
    }

    public List<String> getCitations() {
        return __CITATIONS__;
    }

    public void setCitations(List<String> citations) {
        this.__CITATIONS__ = citations;
    }
//here is query json entry read
    public void setDocumentRelevanceMap(HashMap<String,Boolean> map)
    {
    	this.__DOC_REL_MAP__=map;
    }
    
    public HashMap<String,Boolean> getDocumentRelevanceMap()
    {
    	return this.__DOC_REL_MAP__;
    }
    
    public void setListDocumentRelevanceMap(List<Pair<String, Boolean>> list_rel_docs) 
    {
		this.__LIST_DOC_REL_MAP__=list_rel_docs;
	}
    
    public List<Pair<String, Boolean>> getListDocumentRelevanceMap()
    {
    	return this.__LIST_DOC_REL_MAP__;
    }
    
    public void setFrequency(double frequency)
    {
    	this.__FREQUENCY__=frequency;
    }
    
    public double getFrequency()
    {
    	return this.__FREQUENCY__;
    }
    
    public void setQueryID(Long queryid)
    {
    	this.__QUERY_ID__=queryid;
    }
    
    public Long getQueryID()
    {
    	return this.__QUERY_ID__;
    }
    
    public void setQuery(String query)
    {
    	this.__QUERY__=query;
    }
    
    public String getQuery()
    {
    	return this.__QUERY__;
    }
    
    /**
     *
     * @return
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (__TITLE__ != null) {
            sb.append("Title: ").append(__TITLE__).append("\n");
        }
        if (__ABSTRACT__ != null) {
            sb.append("Abstract: ").append(__ABSTRACT__).append("\n");
        }
        if (__ENTITIES__ != null) {
            boolean first = true;
            sb.append("Entities:");
            for (String entity : __ENTITIES__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(entity);
            }
            sb.append("\n");
        }
        if (__FIELDS_OF_STUDY__ != null) {
            boolean first = true;
            sb.append("Fields Of Study:");
            for (String field : __FIELDS_OF_STUDY__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(field);
            }
            sb.append("\n");
        }
        if (__AUTHORS__ != null) {
            boolean first = true;

            sb.append("Author Names:");
            for (Pair<String, List<String>> author : __AUTHORS__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(author.getL()); // get the name
                // Also list its ids
                List<String> ids = author.getR();
                sb.append(" => ");
                ids.forEach((id) -> {
                    sb.append(id).append(" ");
                });
            }
            sb.append("\n");
        }
        if (__JOURNAL_NAME__ != null) {
            sb.append("Journal Name: ").append(__JOURNAL_NAME__).append("\n");
        }
        if (__VENUE__ != null) {
            sb.append("Venue: ").append(__VENUE__).append("\n");
        }
        if (__SOURCES__ != null) {
            boolean first = true;
            sb.append("Sources:");
            for (String source : __SOURCES__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(source);
            }
            sb.append("\n");
        }
        if (__YEAR__ != 0) {
            sb.append("Year: ").append(__YEAR__).append("\n");
        }

        if (__CITATIONS__ != null) {
            boolean first = true;

            sb.append("Citations IDs:");
            for (String citation : __CITATIONS__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(citation); // get the name
            }
            sb.append("\n");
        }

        return sb.toString();
    }

	

}
