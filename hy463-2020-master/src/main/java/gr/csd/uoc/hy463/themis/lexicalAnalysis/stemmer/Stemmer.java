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
    !! Code from mitos web search engine, old hy463 project !!
 */
package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

/**
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.english.EnglishStemmer;
import javax.swing.*;

/*
 * Stemmer Class. A Singleton class responsible for stemming tokes.
 * This code is only for English!
 *
 */
public class Stemmer {

    private Stemmer() {
    }

    public static String Stem(String string) {
        // To lower case
        string = string.trim().toLowerCase();
        // If emptry return.
        if (string.length() == 0) {
            return string;
        }
        // Java uses Unicode
        // Modern Greek in unicode like ISO 8859-7 are between 0370 - 03FF
        // Basic Latin 0021 - 007E
        // !TODO Currently we do not stem any string that does not start with a ascii character
        char c = string.charAt(0); // Get the first character of the word
        if (c >= (char) 880 && c <= (char) 1023) // If character is between
        // 0x0370 - 0x03FF
        {
            return string;
            //GreekStemmer.Singleton().Stem(string); // return the greek
        } // stemmer
        else if (c >= (char) 33 && c <= (char) 126) // // If character is
        // between 0x0021 - 0x007E
        {
            return EnglishStemmer.Singleton().Stem(string); // return the
        } // english stemmer
        else {
            return string; // In all other cases return string as is
        }
    }

    public static void Initialize() {
        EnglishStemmer.Singleton();
    }

    public static void anotherMain(String[] a) {
        String w;
        Stemmer.Initialize();

        do {
            w = JOptionPane.showInputDialog("Please input a word");
            JOptionPane.showMessageDialog(null, Stemmer.Stem(w), w,
                    JOptionPane.INFORMATION_MESSAGE);
        } while (!w.equals(""));
    }

    public static void main(String[] args) {
        Stemmer.Initialize();
        System.out.println(Stemmer.Stem("ending"));
        System.out.println(Stemmer.Stem("publications"));

    }

} // class Stemmer

