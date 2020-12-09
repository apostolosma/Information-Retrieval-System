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
package gr.csd.uoc.hy463.themis.retrieval;

/**
 * This class represents a query term. A query term can have some kind of
 * weight! Might be useful for experimenting with different weights for terms
 * (e.g., synonyms/antonyms)
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class QueryTerm {

    private double weight = 1.0;
    private String term = null;

    public QueryTerm(String term) {
        this.term = term;
    }

    public QueryTerm(String term, double weight) {
        this.term = term;
        this.weight = weight;

    }

    public void setTerm(String term)
    {
    	this.term=term;
    }
    
    public String getTerm()
    {
    	return this.term;
    }
    
    public void setWeight(Double weight)
    {
    	this.weight=weight;
    }
    
    public Double getWeight()
    {
    	return this.weight;
    }
}
