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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that holds all textual information read from an entry of the collection
 * It is used by the S2JSONEntryReader
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class S2GraphEntry {

    private static final Logger __LOGGER__ = LogManager.getLogger(S2GraphEntry.class);
    private String __ID__ = null;
    private List<String> __AUTHORS__ = null;
    private List<String> __CITATIONS__ = null;

    public String getId() {
        return __ID__;
    }

    public void setId(String id) {
        this.__ID__ = id;
    }

    public List<String> getAuthors() {
        return __AUTHORS__;
    }

    public void setAuthors(List<String> authors) {
        this.__AUTHORS__ = authors;
    }

    public List<String> getCitations() {
        return __CITATIONS__;
    }

    public void setCitations(List<String> citations) {
        this.__CITATIONS__ = citations;
    }

    /**
     *
     * @return
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (__AUTHORS__ != null) {
            boolean first = true;

            sb.append("Author IDs:");
            for (String author : __AUTHORS__) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(author); // get the name
            }
            sb.append("\n");
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
