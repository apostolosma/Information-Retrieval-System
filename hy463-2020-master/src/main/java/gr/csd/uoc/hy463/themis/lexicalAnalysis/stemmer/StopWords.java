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
package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

/**
 * Class responsible for identifying stopwords
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.StringTokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StopWords {

    private static final Logger __LOGGER__ = LogManager.getLogger(StopWords.class);
    private static HashSet<String> __WORDS__;
    private static StopWords __STOPWORDS__ = null;
    private static String __STOPLIST_PATH__ = "stopwords.txt";

    /**
     * Words constructor, initializes stoplist
     */
    private StopWords() {
        BufferedReader br;
        StringBuffer strBuf;
        __WORDS__ = new HashSet<String>();

        try {
            br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/stopwords.txt")));
            strBuf = new StringBuffer();

            while (true) {
                int ch = br.read();
                if (ch == -1) {
                    break;
                }
                strBuf.append((char) ch);
            }

            StringTokenizer strTok = new StringTokenizer(strBuf.toString());
            while (strTok.hasMoreTokens()) {
                __WORDS__.add(strTok.nextToken().toLowerCase());
            }

        } catch (FileNotFoundException e) {
            __LOGGER__.error("Could not load stopwords file!");
        } catch (IOException e) {
            __LOGGER__.error("IOException found!");
        }

    }

    /**
     * Initialize stopwords. Add the stopwords from file
     */
    public static void Initialize() {
        __STOPWORDS__ = (__STOPWORDS__ == null) ? new StopWords() : __STOPWORDS__;
    }

    /**
     * Checks if a word is a stop word
     *
     * @param word The term to be checked
     *
     * @return true if the term is a stop word, false otherwise
     */
    public static boolean isStopWord(String word) {
        Initialize();

        return __WORDS__.contains(word);
    }

    /**
     * Checks if a word is an operator word
     *
     * @param word The term to be checked
     *
     * @return true if the term is an operator word ("and","or","not"), false
     * otherwise
     */
    /*---------------------------------------------------*/
    public static final boolean isOpWord(String word) {
        Initialize();
        return word.compareToIgnoreCase("and") == 0
                || word.compareToIgnoreCase("or") == 0
                || word.compareToIgnoreCase("not") == 0;
    }

    public static void main(String[] args) {
        StopWords.Initialize();
        System.out.println(StopWords.isStopWord("ending")); // false
        System.out.println(StopWords.isStopWord("other"));  // true

    }
}
