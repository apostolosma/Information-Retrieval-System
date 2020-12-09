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
package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.List;

/**
 * This is an abstract class that each retrieval model should extend
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public abstract class ARetrievalModel {

    public enum RESULT_TYPE {
        PLAIN, ESSENTIAL, FULL
    }

    private Indexer indexer;

    public ARetrievalModel(Indexer indexer) {
        this.indexer = indexer;
    }

    /**
     * Method that evaluates the query and returns a ranked list of pairs of the
     * whole relevant documents.
     *
     * If type PLAIN then the Object in the Pair is the id of the doc (String),
     * if the type is ESSENTIAL the Object in the Pair is DocInfoEssential, and
     * if the type is FULL then the Object in the Pair is DocInfoFull.
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score
     *
     * @param query list of query terms
     * @param type the type of object in the pair (PLAIN/ESSENTIAL/FULL)
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, RESULT_TYPE type) throws IOException;

    /**
     * Method that evaluates the query and returns a list of pairs with the
     * top-k ranked results.
     *
     * There are various policies to be faster when doing this if we do not want
     * to compute the scores of all queries.
     *
     * For example by sorting the terms of the query based on some indicator of
     * goodness and process the terms in this order (e.g., cutoff based on
     * document frequency, cutoff based on maximum estimated weight, and cutoff
     * based on the weight of a disk page in the posting list
     *
     * If type PLAIN then the Object in the Pair is the id of the doc (String),
     * if the type is ESSENTIAL the Object in the Pair is DocInfoEssential, and
     * if the type is FULL then the Object in the Pair is DocInfoFull
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score
     *
     * @param query list of query terms
     * @param type the type of object in the pair (PLAIN/ESSENTIAL/FULL)
     * @param topk a number (i.e. the top-10 results)
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, RESULT_TYPE type, int topk) throws IOException;

    // We should also add some kind of paging and caching... but maybe in the future
}
