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
package gr.csd.uoc.hy463.themis.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that holds configuration options for the themis system
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class Config {

    private static final Logger __LOGGER__ = LogManager.getLogger(Config.class);
    private Properties __PROP__;

    public Config() throws IOException {
        __PROP__ = new Properties();
        String propFileName = "/themis.config";

        InputStream input = getClass().getResourceAsStream(propFileName);

        if (input != null) {
            __PROP__.load(input);
        } else {
            __LOGGER__.error("property file '" + propFileName + "' not found in the classpath");
        }
    }

    /**
     * Check if config is valid
     *
     * @return
     */
    public boolean valid() {
        return !__PROP__.isEmpty();
    }

    /**
     * a property about the index path
     *
     * @return
     */
    public String getIndexPath() {
        return __PROP__.getProperty("INDEX_PATH");
    }

    public String getContentsFileName() {return __PROP__.getProperty("CONTENTS_FILENAME");}

    public String getOffsetsFileName() {return __PROP__.getProperty("OFFSETS_FILENAME");}

   // public String getEncodingsFileName() {return __PROP__.getProperty("ENCODINGS_FILENAME");}
   
    public Double getPagerankThreshold() {return Double.parseDouble(__PROP__.getProperty("PAGERANK_THRESHOLD"));}
    
    public String getEncodings_OffsetsFileName() {return __PROP__.getProperty("ENCODINGS_OFFSETS_FILENAME");}

    public String getQuerySet_Path() {return __PROP__.getProperty("QUERYSET_PATH");}
    
    public String getQuerySet_FileName() {return __PROP__.getProperty("QUERYSET_FILENAME");}
    
    public String getInfoType() {return __PROP__.getProperty("TYPE");}

    public String getB2MetricsResults_FileName() {return __PROP__.getProperty("B2RESULTS_METRICS_FILENAME");}
    
    public String getB2ModelsResults_FileName() {return __PROP__.getProperty("B2RESULTS_MODELS_FILENAME");}
    
    public String getB3ModelsResults_FileName() {return __PROP__.getProperty("B3RESULTS_MODELS_FILENAME");}
    
    public String getB3MetricsResults_FileName() {return __PROP__.getProperty("B3RESULTS_METRICS_FILENAME");}
    
    public String getB4ModelsResults_FileName() {return __PROP__.getProperty("B4RESULTS_MODELS_FILENAME");}
    
    public String getB4MetricsResults_FileName() {return __PROP__.getProperty("B4RESULTS_METRICS_FILENAME");}

    public String getB6ModelsResults_FileName() {return __PROP__.getProperty("B6RESULTS_MODELS_FILENAME");}
    
    public String getB6MetricsResults_FileName() {return __PROP__.getProperty("B6RESULTS_METRICS_FILENAME");}

    	
    
   // public String getResults_FileName() {return __PROP__.getProperty("RESULTS");}
    
    public String getResults_Path() {return __PROP__.getProperty("RESULTS_PATH");}
    
    /**
     * a property about the dataset path
     *
     * @return
     */
    public String getDatasetPath() {
        return __PROP__.getProperty("DATASET_PATH");
    }

    /**
     * Returns the filename of vocabulary index
     *
     * @return
     */
    public String getVocabularyFileName() {
        return __PROP__.getProperty("VOCABULARY_FILENAME");
    }

    /**
     * Returns the filename of postings index
     *
     * @return
     */
    public String getPostingsFileName() {
        return __PROP__.getProperty("POSTINGS_FILENAME");
    }

    /**
     * Returns the filename of documents index
     *
     * @return
     */
    public String getDocumentsFileName() {
        return __PROP__.getProperty("DOCUMENTS_FILENAME");
    }

    /**
     * Returns the filename of meta index, holding meta information about the
     * index
     *
     * @return
     */
    public String getMetaFileName() {
        return __PROP__.getProperty("META_FILENAME");
    }

    /**
     * Returns the default retrieval model (VSM/BM25)
     *
     * @return
     */
    public String getRetrievalModel() {
        return __PROP__.getProperty("RETRIEVAL_MODEL");
    }

    /**
     * Returns the weight for the retrieval model
     *
     * @return
     */
    public double getRetrievalModelWeight() {
        return Double.parseDouble(__PROP__.getProperty("RETRIEVAL_MODEL_WEIGHT"));
    }

    /**
     * Returns the weight for the publications pagerank weight
     *
     * @return
     */
    public double getPagerankPublicationsWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_PUBLICATIONS_WEIGHT"));
    }

    /**
     * Returns the weight for the authors pagerank weight
     *
     * @return
     */
    public double getPagerankAuthorsWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_AUTHOR_WEIGHT"));
    }

    /**
     * Returns if we should user the stemmer
     *
     * @return
     */
    public boolean getUseStemmer() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STEMMER"));
    }

    /**
     * Returns if we should use the stopwords
     *
     * @return
     */
    public boolean getUseStopwords() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STOPWORDS"));
    }

    /**
     * Returns if we should use the titles
     *
     * @return
     */
    public boolean getUseTitles() {
        return Boolean.parseBoolean(__PROP__.getProperty("TITLES_ENABLED"));
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getTitlesFileName() {
        return __PROP__.getProperty("TITLES_PATH");
    }

    /**
     * number of max number of files per each partial index
     *
     * @return
     */
    public int getPartialIndexSize() {
        String size = __PROP__.getProperty("PARTIAL_INDEX_MAX_DOCS_SIZE");
        if (size != null) {
            return Integer.parseInt(size);
        } else {
            return 0;
        }
    }

    /**
     * Returns the path to the compressed word2vec model
     *
     * @return
     */
    public String getWord2VecModelFileName() {
        return __PROP__.getProperty("WORD2VEC_FILENAME");
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getGloveModelFileName() {
        return __PROP__.getProperty("GLOVE_FILENAME");
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getJudgmentsFileName() {
        return __PROP__.getProperty("JUDGMENTS_PATH");
    }

    /**
     * number of max memory to use
     *
     * @return
     */
    public long getMaxMemory() {
        String size = __PROP__.getProperty("MAX_MEMORY");
        // make it lowercase
        size = size.toUpperCase().trim();
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([KMGTP]?B)");
        Matcher m = p.matcher(size);
        List<String> parts = new ArrayList<>();

        // We have two groups in the pattern
        while (m.find()) {
            parts.add(m.group(1));
            parts.add(m.group(2));
        }

        System.out.println(parts);
        long bytes = 0;
        // metric unit should be given
        if (parts.size() == 2) {
            String unit = parts.get(1);
            boolean useFloat = true;

            switch (unit) {
                case "B":
                    bytes = 1;
                    useFloat = false;
                    break;
                case "KB":
                    bytes = 1024;
                    break;
                case "MB":
                    bytes = 1024 * 1024;
                    break;
                case "GB":
                    bytes = 1024 * 1024 * 1024;
                    break;
                case "TB":
                    // keep dreaming....!
                    bytes = 1024 * 1024 * 1024 * 1024;  // not supported by long!
                    break;
                case "PB":
                    // probably in another life!
                    bytes = 1024 * 1024 * 1024 * 1024 * 1024; // not supported by long!
                    break;
            }
            if (useFloat) {
                return Math.round(bytes * Float.parseFloat(parts.get(0)));
            } else {
                return bytes * ((long) Float.parseFloat(parts.get(0)));
            }
        }

        return bytes;
    }

}
