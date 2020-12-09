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

 /*
    !! Code from mitos web search engine !!
 */
package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.english;

/**
 * English Stemmer
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class EnglishStemmer {

    private static EnglishStemmer m_englishStemmer = null;
    private PorterStemmer m_PorterStemmer;

    private EnglishStemmer() {

        m_PorterStemmer = new PorterStemmer();
    }

    public static EnglishStemmer Singleton() {

        return (m_englishStemmer == null) ? (m_englishStemmer = new EnglishStemmer())
                : m_englishStemmer;
    }

    public String Stem(String _word) {

        String word = _word;
        m_PorterStemmer.add(word.toCharArray(), word.length());
        m_PorterStemmer.stem();
        return m_PorterStemmer.toString();
    }

} // class EnglishStemmer
