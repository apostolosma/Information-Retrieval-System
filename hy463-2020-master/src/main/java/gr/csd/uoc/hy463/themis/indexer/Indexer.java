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
package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2GraphEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.Graph;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.Node;
import gr.csd.uoc.hy463.themis.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.util.*;



/**
 * Our basic indexer class. This class is responsible for two tasks:
 *
 * a) Create the appropriate indexes given a specific directory with files (in
 * our case the Semantic Scholar collection)
 *
 * b) Given a path load the indexes (if they exist) and provide information
 * about the indexed data, that can be used for implementing any kind of
 * retrieval models
 *
 * When the indexes have been created we should have three files, as documented
 * in Index.java
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class Indexer {

    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private Config __CONFIG__;  // configuration options
    // The file path of indexes
    private String __INDEX_PATH__ = null;
    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;
    private String __CONTENTS_FILENAME__ = null;
    private static String __META_FILENAME__ = null;
    private static boolean __STOPWORDS__;
    private static boolean __STEMMER__;


    //Is used to store documents information for creating the documents file.
    private static HashMap<String,DocumentStructure> docsBatch = new HashMap<String,DocumentStructure>();
    //Is used to store document's ID, for example if we have read 10 articles and we are going to read the 11th
	//we put in this map the entry.getID() with a key the number 11.
    //private static HashMap<Integer,String> keyEncoding = new HashMap<Integer,String>();
	//private static HashMap<String, Pair<Long,Long>> wpOffsets = new HashMap<String,Pair<Long,Long>>();
	private static HashMap<Integer,Pair<String,Long>> keyEnc_Offsets=new HashMap<>(131072);

    //--MERGING PROCCESS--
	private BufferedReader rdVoc1;
	private BufferedReader rdVoc2;
	private BufferedWriter wrVoc;
	private int pageRankIterations = 0;
	String maxNode = null;

	//Using a TreeMap because it supports sorted insertion, using StringComparator to sort each insertion.
  	//As a key it has a string , which represents each word,and as value a list of Occurrences.
  	//Occurrences is a custom class which holds a string and a number, a string which denotes the field key word appeared in,
  	//and a number which denotes the number of times this word appeared in the !specific! field.
  	//E.g. |Word|        => { Field/NumOfOccurrences}
	// 	   |each|        => {Title -> 1} -> {Abstract -> 3} ... etc.
	static HashMap<String,ArrayList<Occurrence>> miniVocab  = null;
	static TreeMap<String,ArrayList<FullOccurence>> fVocab = new TreeMap<>(new StringComparator());
	//Used to store the Postings per term.Each Posting Structure is a TreeMap with key the Doc_ID and value the tf.
	//The ArrayList contains each term's treemap which is the data above.

    // Vocabulary should be stored in memory for querying! This is crucial
    // since we want to keep things fast! This is done through load().
    // For this project use a HashMap instead of a trie
    private HashMap<String, Pair<Integer, Long>> __VOCABULARY__ = new HashMap<String,Pair<Integer,Long>>(2097152);
    private RandomAccessFile __POSTINGS__ = null;
    private RandomAccessFile __DOCUMENTS__ = null;
    private RandomAccessFile __CONTENTS__ = null;

    // This map holds any information related with the indexed collection
    // and should be serialized when the index process has finished. Such
    // information could be the avgDL for the Okapi-BM25 implementation,
    // a timestamp of when the indexing process finished, the path of the indexed
    // collection, the options for stemming and stop-words used in the indexing process,
    // and whatever else you might want. But make sure that before querying
    // the serialized file is loaded
    private Map<String, String> __META_INDEX_INFO__ = new HashMap<String,String>();;
    private HashMap<String,Double> __PAGE_RANK__ = new HashMap<>();
    private static long allDocsLength = 0;
    public int allArticles = 0;
    private double avgPR;
    public double avgDL;

	/**
     * Default constructor. Creates also a config instance
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Indexer() throws IOException, ClassNotFoundException {
        __CONFIG__ = new Config();  // reads info from themis.config file
        init();
    }

    /**
     * Constructor that gets a current Config instance
     *
     * @param config
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Indexer(Config config) throws IOException, ClassNotFoundException {
        this.__CONFIG__ = config;  // reads info from themis.config file
        init();
    }
	/**
	 * This method is responsible for A2 subquestion.
	 * @param dir
	 * @throws IOException
	 */
    public static void A1(File dir) throws IOException {
		File[] dirListings = dir.listFiles();
		int vocabSize=0;
		//A1
		BufferedReader bdr = new BufferedReader(new FileReader(dirListings[3].getPath()));
		String publication = bdr.readLine();

		S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(publication);
		publication = stringPreparation(entry);
		StringTokenizer bodyToken = new StringTokenizer(publication,"\n");
		int fieldFlag = 0;

		while(bodyToken.hasMoreTokens()) {
			StringTokenizer tk = new StringTokenizer(bodyToken.nextToken(),":");
			A1occur(tk.nextToken(),tk.nextToken());
		}
	}

	/**
	 * This method is responsible for A2 subquestion.
	 * @param dir
	 * @throws IOException
	 */
	public static void A2(File dir) throws IOException
	{
		File[] dirListings = dir.listFiles();
		BufferedReader bdr2 = new BufferedReader(new FileReader(dirListings[0].getAbsolutePath()));
			String article;
			while((article = bdr2.readLine()) != null)
			{
				S2TextualEntry localEntry = S2JsonEntryReader.readTextualEntry(article);
				article = stringPreparation(localEntry);
				StringTokenizer strField = new StringTokenizer(article,"\n");
				while(strField.hasMoreTokens())
				{
					StringTokenizer tk = new StringTokenizer(strField.nextToken(),":");
					A1occur(tk.nextToken(),tk.nextToken());
				}
			}
	}

	/**
	 * This method find occurrencies for A1&A2.
	 * @param field
	 * @param body
	 */
    public static void A1occur(String field,String body)
	{
		int occ = 0;

		//Spame to string tou pedio se kathe le3i
		StringTokenizer token = new StringTokenizer(body," ");
		//Iterating through each word and checking if miniVocab already contains it.
		//1) If it contains the word and there is another occurrence in its list in the same field just
		// increment the number of occurrences in this field.
		//2) If it contains the word but this is the first occurrence in this field just create a new Occurrence with number 1
		//   and add it in the already existing list. (You just created a new occurrence for this word in this field)
		//3) If it doesn't contain the word, just create a new occurrence with #occurrences equal to 1,also create a list of occurrences
		//   and add the fresh occurrence in the list, then pass the list in the miniVocab with key the word and value the list of occurrences.

		while(token.hasMoreTokens())
		{
			boolean found = false;
			//boolean incrementDf;
			ArrayList<FullOccurence> occurs=null;

			String temp = token.nextToken();
			temp.toLowerCase();

			if(fVocab.containsKey(temp))//if term exists
			{
				occurs = fVocab.get(temp);
				for(FullOccurence occur : occurs)
				{
					//This means we have seen this term in the same field again
					if(field.equals(occur.getField()))
					{
						occur.setTf(occur.getTf() + 1);
						found = true;
					}
				}
				//We've seen this term before but not in this field.
				if(found == false)
				{
					FullOccurence newOccur = new FullOccurence(field,1);
					occurs.add(newOccur);
					fVocab.put(temp,occurs);
				}


			}
			//Here we haven't seen this term till now, so we create a new Occurrence and add the word in the
			// vocabulary,we also add the document which this word appears in(DocID) and place where it occurred(new occurrence)
			else
			{
				FullOccurence newOccur = new FullOccurence(field,1);
				occurs = new ArrayList<>();
				occurs.add(newOccur);
				fVocab.put(temp,occurs);
			}
		}
	}

	/**
	 * Same as checkForOccurrencies but now we apply both stemming and removing stopwords.
	 */
	public static int ApplyBoth(int articleId,String body)
	{
		boolean found = false,alreadyExists = false;
		ArrayList<Occurrence> occur = null;
		StringTokenizer token = new StringTokenizer(body," ");
		int documentLength = token.countTokens();
		while(token.hasMoreTokens())
		{
			String s = token.nextToken();
			if(!StopWords.isStopWord(s) && !StopWords.isOpWord(s)) {
				String stemmed = Stemmer.Stem(s);
				occur=miniVocab.get(stemmed);
				if(occur!=null)
				{
					//iterator to go through the list from the end to the beginning
					ListIterator<Occurrence> listIter = occur.listIterator(occur.size());
					while (listIter.hasPrevious()) {
					    Occurrence prev = listIter.previous();
					    //if the max ID article in the list is < from the current one then it is a new one(higher id doc)
					    //so break to insert a new occurrence of it.
					    if(prev.getDocId()<articleId) {break;}
					    else if (prev.getDocId() == articleId)
						{
							prev.setTf(prev.getTf() + 1);
							found = true;
						}
					}
					if (found == false) {
						Occurrence newOccur = new Occurrence(articleId, 1);
						miniVocab.get(stemmed).add(newOccur);
					}
					else
					{
						found=false;
					}
					
				}
				else
				{
					Occurrence newOccur = new Occurrence(articleId, 1);
					occur = new ArrayList<>();
					occur.add(newOccur);
					miniVocab.put(stemmed, occur);
				}
			}
		}
		return documentLength;
	}


	/*!TODO*/
	/** We remove the stopwords from the text:
	 *
	 *  Same as checkForOccurrencies but now we also remove stopwords.
	 */
	public static int RemoveStopwords(int articleId,String body)
	{
		boolean found = false;
		ArrayList<Occurrence> occur = null;
		StringTokenizer token = new StringTokenizer(body," ");
		int documentLength = token.countTokens();
		while(token.hasMoreTokens())
		{
			String s = token.nextToken();
			if(!StopWords.isStopWord(s) && !StopWords.isOpWord(s)) {
				if(miniVocab.containsKey(s))
				{
					occur = miniVocab.get(s);
					for (Occurrence oc : occur) {
						if (oc.getDocId() == articleId) {
							oc.setTf(oc.getTf() + 1);
							found = true;
						}
					}
					if (found == false) {
						Occurrence newOccur = new Occurrence(articleId, 1);
						miniVocab.get(s).add(newOccur);
					}
				} else {
					Occurrence newOccur = new Occurrence(articleId, 1);
					occur = new ArrayList<>();
					occur.add(newOccur);
					miniVocab.put(s, occur);
				}
			}
		}
		return documentLength;
	}

	/*!TODO*/
	/**Stemming the Terms of the set
	 * Same as CheckForOccurrencies but now we aπply stemming.
	 * @param articleId Doc currently working at.
	 * @param body Field's body
	 */
	public static int ApplyStemming(int articleId,String body)
	{
		boolean found = false;
		ArrayList<Occurrence> occur = null;
		StringTokenizer token = new StringTokenizer(body," ");
		int documentLength = token.countTokens();
		while(token.hasMoreTokens())
		{
			String s = token.nextToken();
			String stemmed = Stemmer.Stem(s);
			if(miniVocab.containsKey(stemmed))
			{
				occur = miniVocab.get(stemmed);
				for (Occurrence oc : occur) {
					if (oc.getDocId() == articleId) {
						oc.setTf(oc.getTf() + 1);
						found = true;
					}
				}
				if (found == false) {
					Occurrence newOccur = new Occurrence(articleId, 1);
					miniVocab.get(stemmed).add(newOccur);
				}
			} else {
				Occurrence newOccur = new Occurrence(articleId, 1);
				occur = new ArrayList<>();
				occur.add(newOccur);
				miniVocab.put(stemmed, occur);
			}
		}
		return documentLength;
	}


	/**Α3
		*cases:
		*1)Stopword=true,stemming=true
		*ApplyStemming(RemoveStopwords());
		*2)Stopword=true,stemming=false
		*temp=RemoveStopwords();
		*3)Stopwords=false,stemming=true
		*ApplyStemming(miniVocab.keySet());
		*A4)Then Create Vocabulary
		*1)createVocabulary(temp);
		*/
	
	static long storeD_ST=0;
	static long storeD_ET=0;
	static long storeD_final=0;
	
	static long replace_ST=0;
	static long replace_ET=0;
	static long replace_final=0;
	
	static long body_ST=0;
	static long body_ET=0;
	static long body_final=0;
	
	static long apply_ST=0;
	static long apply_ET=0;
	static long apply_final=0;
	public static void actions(int articleId,S2TextualEntry entry) {
		body_ST=System.nanoTime();
		String body = entry.getTitle().replaceAll("\n", " ")
				+ entry.getPaperAbstract().replaceAll("\n", " ")+ " "
				+ entry.getEntities().toString().replaceAll("\n", " ")+ " "
				+ entry.getFieldsOfStudy().toString().replaceAll("\n", " ")+ " "
				+ entry.getAuthors().toString().replaceAll("\n", " ")+ " "
				+ entry.getJournalName().replaceAll("\n", " ")
				+ entry.getVenue().replaceAll("\n", " ")
				+ entry.getSources().toString().replaceAll("\n", " ")+ " "
				+ Integer.toString(entry.getYear()) ;
		body_ET=System.nanoTime();
		body_final+=body_ET-body_ST;
		replace_ST=System.nanoTime();
		
		body = body.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
		body = body.replaceAll("[0-9]"," ");
		body = body.toLowerCase();
		replace_ET=System.nanoTime();
		replace_final+=replace_ET-replace_ST;
		if(__STOPWORDS__ && __STEMMER__)//create vocab with stemming and stopwording
		{
				apply_ST=System.nanoTime();
				int documentLength = ApplyBoth(articleId,body);
				apply_ET=System.nanoTime();
				apply_final+=apply_ET-apply_ST;
				allDocsLength += documentLength;
				storeD_ST=System.nanoTime();
				StoreDocuments(entry,documentLength);
				storeD_ET=System.nanoTime();
				storeD_final+=storeD_ET-storeD_ST;
				
		}
		else if(__STOPWORDS__ && !__STEMMER__)//create vocab without stemming but with stopwording
		{
				int documentLength = RemoveStopwords(articleId,body);
				allDocsLength += documentLength;
				StoreDocuments(entry,documentLength);
		}
		else if(!__STOPWORDS__ && __STEMMER__)//create vocab with stemming but without stopwording
		{
				int documentLength = ApplyStemming(articleId,body);
				allDocsLength += documentLength;
				StoreDocuments(entry,documentLength);
		}
		else//create vocab without stemming and stopwording
		{
				int documentLength = checkForOccurrencies(articleId,body);
				allDocsLength += documentLength;
				StoreDocuments(entry,documentLength);
		}
	}

	/**
	 *Creating a string from the S2TextualEntry (which is our article) and reforming it in
	 *such a way that you can tokenize this string, and each token is a field of this entry.(title,paper abstract ... etc.).
	 *It also removes chacacters which unite two words together ( "." , "," ...)
	 *E.g. each,two => each two
	 */
	public static String stringPreparation(S2TextualEntry entry)
	{
			String token = 	"Title: " + entry.getTitle().replaceAll("\n", "") + "\n"
			  	+ "Abstract: " + entry.getPaperAbstract().replaceAll("\n", "")  + "\n"
			  	+ "Entities: " + entry.getEntities().toString().replaceAll("\n", "") + "\n"
				+ "Fields of study: " + entry.getFieldsOfStudy().toString().replaceAll("\n", "") + "\n"
				+ "Authors: " + entry.getAuthors().toString().replaceAll("\n", "")+ "\n"
				+ "Journal Name: " +entry.getJournalName().replaceAll("\n", "") + "\n"
				+ "Venue: " + entry.getVenue().replaceAll("\n", "") + "\n"
				+ "Sources: " + entry.getSources().toString().replaceAll("\n", "") + "\n"
				+ "Year: " + Integer.toString(entry.getYear()) + "\n";
			token = token.replaceAll("[-(=)'!/\"{?}#$%&*/+;<>@_^`～~×’|\\\\]*","");
			token = token.replaceAll("\\[","");
			token = token.replaceAll("[,.\\]]"," ");
			token = token.replaceAll("^[A-Za-z]","");

		return token;
	}

	/**
	 * Using the string created after calling stringPreparation on publication string, which is created
	 *in a way to be able to split each field in pieces. Which means, after tokenizing the stringPreparation string
	 *in each token you get each field of the publication(title,paper abstract ... etc.).
	 */
	public static int checkForOccurrencies(int articleId,String body)
	{
		boolean found = false;
		ArrayList<Occurrence> occur = null;
		StringTokenizer token = new StringTokenizer(body," ");
		int documentLength = token.countTokens();
		while(token.hasMoreTokens())
		{
			String s = token.nextToken();
			if(miniVocab.containsKey(s))
			{
				occur = miniVocab.get(s);
				for (Occurrence oc : occur) {
					if (oc.getDocId() == articleId) {
						oc.setTf(oc.getTf() + 1);
						found = true;
					}
				}
				if (found == false) {
					Occurrence newOccur = new Occurrence(articleId, 1);
					miniVocab.get(s).add(newOccur);
				}
			} else {
				Occurrence newOccur = new Occurrence(articleId, 1);
				occur = new ArrayList<>();
				occur.add(newOccur);
				miniVocab.put(s, occur);
			}
		}
		return documentLength;
	}



	/**
     * Initialize things
     */
    private void init() {
        __VOCABULARY_FILENAME__ = __CONFIG__.getVocabularyFileName();
        __POSTINGS_FILENAME__ = __CONFIG__.getPostingsFileName();
        __DOCUMENTS_FILENAME__ = __CONFIG__.getDocumentsFileName();
	__CONTENTS_FILENAME__=__CONFIG__.getContentsFileName();
        __INDEX_PATH__ = __CONFIG__.getIndexPath();
        __STEMMER__ = __CONFIG__.getUseStemmer();
        __STOPWORDS__ = __CONFIG__.getUseStopwords();
		__META_FILENAME__ = __CONFIG__.getMetaFileName();
        StopWords.Initialize();
        Stemmer.Initialize();
    }

    /**
     * Checks that the index path + all *.idx files exist
     *
     * Method that checks if we have all appropriate files
     *
     * @return
     */
    public boolean hasIndex() {
        // Check if path exists
        File file = new File(__INDEX_PATH__);
        if (!file.exists() || !file.isDirectory()) {
            __LOGGER__.error(__INDEX_PATH__ + "directory does not exist!");
            return false;
        }
        // Check if index files exist
        file = new File(__INDEX_PATH__ + __VOCABULARY_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__VOCABULARY_FILENAME__ + "vocabulary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        file = new File(__INDEX_PATH__ + __POSTINGS_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__POSTINGS_FILENAME__ + " posting binary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        file = new File(__INDEX_PATH__ + __DOCUMENTS_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_FILENAME__ + "documents binary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        return true;
    }

    /**
     * Method responsible for indexing a directory of files
     *
     * If the number of files is larger than the PARTIAL_INDEX_MAX_DOCS_SIZE set
     * to the themis.config file then we have to dump all data read up to now to
     * a partial index and continue with a new index. After creating all partial
     * indexes then we have to merge them to create the final index that will be
     * stored in the file path.
     *
     * Can also be modified to use the MAX_MEMORY usage parameter given in
     * themis.conf for brave hearts!
     *
     * @param path
     * @throws IOException
     */
    public boolean index(String path) throws IOException {
    	miniVocab = new HashMap<>(2097152);
		Index index = new Index(__CONFIG__);
		int id = 0;
		index.setID(id+100);
		long st, et,D_timer = 0,M_timer = 0;
		long reading = 0;
		long ST_actions=0,ET_actions=0,f_actions=0;
		long allPub_ST=0,allPub_ET=0,allPub_final=0;

		// Holds  all files in path
//        List<String> files = new ArrayList<>();
		//*!*Added
		File dir = new File(path);
		File[] dirListings = dir.listFiles();

		// We use a linked list as a queue for our partial indexes
		Queue<Integer> partialIndexesQueue = new LinkedList<>();
		// Add id to queue
		partialIndexesQueue.add(id+100);
		int article = 1;
		// for each file in path
		for (File file : dirListings) {
			st = System.nanoTime();
			//st = System.nanoTime();
			allPub_ST=System.nanoTime();
			List<String> allPublications = Files.readAllLines(file.toPath());
//			BufferedReader publicationString = new BufferedReader(new FileReader(file.getPath()));
			allPub_ET=System.nanoTime();
			allPub_final+=allPub_ET-allPub_ST;
			for(String publication : allPublications){

				S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(publication);
				Pair<String,Long> p=new Pair(entry.getId(),0);
				//keyEncoding.put(allArticles, entry.getId());
				keyEnc_Offsets.put(allArticles, p);
				ST_actions=System.nanoTime();
				actions(allArticles, entry);
				ET_actions=System.nanoTime();
				f_actions+=ET_actions-ST_actions;
//				System.out.println(article);
				if (article == __CONFIG__.getPartialIndexSize()) {
					et = System.nanoTime();
					reading += (et - st);
					et = System.nanoTime();
					index.dump(this);   // dump partial index to appropriate subdirectory
//					System.exit(0);
					st = System.nanoTime();
//					System.out.println("Dump:" + (st-et));
					D_timer += (st - et);
					miniVocab.clear();
					//keyEncoding.clear();
					keyEnc_Offsets.clear();
					docsBatch.clear();
					// Create a new index
					// Increase partial indexes and dump files to appropriate directory
					id++;
					index = new Index(__CONFIG__);
					index.setID(id + 100);
					// Add id to queue
					partialIndexesQueue.add(id + 100);
					article = 0;
				}
				article++;
				allArticles++;
			}
			if(file == dirListings[dirListings.length - 1])
			{
				index.dump(this);
			}
		}
		System.out.println("Finished indexing, going to merge.");
		st = System.nanoTime();
//		System.out.println(partialIndexesQueue.toString());
		System.out.println("Now we are going to merge");
		try {
			merge(partialIndexesQueue);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		et = System.nanoTime();
		M_timer = (et- st);
		__META_INDEX_INFO__.put("ApplyBoth time: ",Long.toString(f_actions));
		__META_INDEX_INFO__.put("All Dump time: ",Long.toString(D_timer));
		__META_INDEX_INFO__.put("Merge time: ",Long.toString(M_timer));
		__META_INDEX_INFO__.put("Reading time: ", Long.toString(reading));
		constructingMeta();
		index.meta(this);
		return false;
    }

	private void constructingMeta() {
		long avgDL = allDocsLength / allArticles;
		System.out.println("average Document Length: " + avgDL + "\t all docs length: " + allDocsLength + "\t all articles: " + allArticles);
		__META_INDEX_INFO__.put("avgDL:",Long.toString(avgDL));
		__META_INDEX_INFO__.put("Stemmer:", String.valueOf(__CONFIG__.getUseStemmer()));
		__META_INDEX_INFO__.put("Stopwords:", String.valueOf(__CONFIG__.getUseStopwords()));
		__META_INDEX_INFO__.put("Index Path:",__CONFIG__.getIndexPath());
		__META_INDEX_INFO__.put("Data Path:",__CONFIG__.getDatasetPath());
		__META_INDEX_INFO__.put("PARTIAL_INDEX_MAX_DOCS_SIZE: ",Integer.toString(__CONFIG__.getPartialIndexSize()));
		__META_INDEX_INFO__.put("MAX_MEMORY: ",Long.toString(__CONFIG__.getMaxMemory()));
	}

	/**
     * This method is used to store all the desired variables in the docsBatch hashmap.
     * @param entry
     */
	static void StoreDocuments(S2TextualEntry entry, int documentLength)
    {
    	DocumentStructure ds = new DocumentStructure();
    	String docsID = entry.getId();
    	ds.setTitle(entry.getTitle());
    	ArrayList<String> autname = new ArrayList<String>();
    	ArrayList<String> id = new ArrayList<String>();
    	for(Pair<String, List<String>> name : entry.getAuthors())
    	{
    		autname.add(name.getL());
    		id.add(name.getR().toString());
    	}
    	ds.setAuthorNames(autname);
    	ds.setAuthorIDs(id);
    	ds.setYear((short) entry.getYear());
    	ds.setJournalName(entry.getJournalName());
    	ds.setDocsLength(documentLength);
    	ds.setPageRank(0.0);
    	docsBatch.put(docsID, ds);
    }


    /**
     * Method that merges the partial indexes and creates a new index with new
     * ID which is either a new partial index or the final index if the queue is
     * empty. If it is a partial index it adds it to the queue at the tail using
     * add
     *
     * @param partialIndexesQueue
     * @return
     */
	public void merge(Queue<Integer> partialIndexesQueue) throws IOException, InterruptedException {
		int id1, id2, df;
		File dir1 = null, dir2 = null, mFile = null;
		Index index = new Index(__CONFIG__);
		File rdVoc1, rdVoc2;
		BufferedWriter wrVoc;
		RandomAccessFile mergedPos, rdPos1, rdPos2;
		while(!partialIndexesQueue.isEmpty()){
			if (partialIndexesQueue.size() == 1) {
				System.out.println("Finishing with merge.");
				return;
			}
			// Repeatevily use the indexes with the ids stored in the head of the queue
			// using the get method
			// Read vocabulary files line by line in corresponding dirs
			// and check which is the shortest lexicographically.
			// Read the corresponding entries in the postings and documents file
			// and append accordingly the new ones
			// If both partial indexes contain the same word, them we have to update
			// the df and append the postings and documents of both
			// Continue with the next lexicographically shortest word
			// Dump the new index and delete the old partial indexes
			if (!partialIndexesQueue.isEmpty()) {
				id1 = partialIndexesQueue.poll();
				id2 = partialIndexesQueue.poll();
				System.out.println(id1 + " " + id2);
				if (id1 == 0) {
					dir1 = new File(getIndexDirectory());
					rdVoc1 = new File(dir1.getPath() + "/" + __VOCABULARY_FILENAME__);
					rdPos1 = new RandomAccessFile(dir1.getPath() + "/" + __POSTINGS_FILENAME__, "r");
				} else {
					dir1 = new File(getIndexDirectory() + id1 + "/");
					dir1.mkdir();
					rdVoc1 = new File(dir1.getPath() + "/" + __VOCABULARY_FILENAME__);
					rdPos1 = new RandomAccessFile(dir1.getPath() + "/" + __POSTINGS_FILENAME__, "r");
				}
				dir2 = new File(getIndexDirectory() + id2);
				dir2.mkdir();
				rdVoc2 = new File(dir2.getPath() + "/" + __VOCABULARY_FILENAME__);
				rdPos2 = new RandomAccessFile(dir2.getPath() + "/" + __POSTINGS_FILENAME__, "r");
				if (partialIndexesQueue.size() == 0)
					mFile = new File(getIndexDirectory());
				else {
					if (id1 != 0)
						mFile = new File(getIndexDirectory() + (id1 + id2));
					else mFile = new File(getIndexDirectory() + (id1 + id2));
					mFile.mkdir();
				}
				wrVoc = new BufferedWriter(new FileWriter(mFile.getPath() + "/" + __VOCABULARY_FILENAME__));
				mergedPos = new RandomAccessFile(mFile.getPath() + "/" + __POSTINGS_FILENAME__, "rw");
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				BufferedReader r1 = new BufferedReader(new FileReader(rdVoc1));
				BufferedReader r2 = new BufferedReader(new FileReader(rdVoc2));
				String lineFrom1 = r1.readLine(), lineFrom2 = r2.readLine();
				int os1, os2;
				long et = 0, st = 0;
				long sizeMemory = 0;
				while (true) {
					if (lineFrom1 == null || lineFrom2 == null) {
						break;
					}
					String[] split1 = lineFrom1.split(" ", 3);
					String[] split2 = lineFrom2.split(" ", 3);
					int compare = split1[0].compareTo(split2[0]);
					//vocab1's word first in lexicographic order
					if (compare <= -1) {
						if (split1[1] != null && split1[2] != null) {
							df = Integer.parseInt(split1[1]);
							wrVoc.write(split1[0] + " " + split1[1] + " " + (sizeMemory + output.size()) + "\n");
							rdPos1.seek(Long.parseLong(split1[2]));
							byte[] rd1 = new byte[df * 16];
							rdPos1.read(rd1, 0, rd1.length);

							os1 = 0;
							for (int i = 0; i < Integer.parseInt(split1[1]); i++) {
								output.write(rd1, os1, 4); // 0
								output.write(rd1, os1 + 4, 4); // 4
								output.write(rd1, os1 + 8, 8); // 8
								os1 += 16;
							}
							sizeMemory += output.size();
							mergedPos.write(output.toByteArray());
							output.reset();
							lineFrom1 = r1.readLine();
						}
					} else if (compare >= 1) {
						if (split2[1] != null && split2[2] != null) {
							wrVoc.write(split2[0] + " " + split2[1] + " " + (sizeMemory + output.size()) + "\n");
							rdPos2.seek(Long.parseLong(split2[2]));
							df = Integer.parseInt(split2[1]);
							byte[] rd2 = new byte[df * 16];
							rdPos2.read(rd2, 0, rd2.length);
							os2 = 0;

							for (int i = 0; i < Integer.parseInt(split2[1]); i++) {
								output.write(rd2, os2, 4); // 0
								output.write(rd2, os2 + 4, 4); // 4
								output.write(rd2, os2 + 8, 8); // 8
								os2 += 16;
							}
							sizeMemory += output.size();
							mergedPos.write(output.toByteArray());
							output.reset();
							lineFrom2 = r2.readLine();
						}
					} else if (compare == 0) {
						if (split1[1] != null && split2[1] != null && split1[2] != null && split2[2] != null) {
							int combinedDf = Integer.parseInt(split1[1]) + Integer.parseInt(split2[1]);
							wrVoc.write(split1[0] + " " + combinedDf + " " + (sizeMemory + output.size()) + "\n");
							os1 = 0;
							rdPos1.seek(Long.parseLong(split1[2]));
							byte[] rd1 = new byte[Integer.parseInt(split1[1]) * 16];
							rdPos1.read(rd1, 0, rd1.length);

							rdPos2.seek(Long.parseLong(split2[2]));
							byte[] rd2 = new byte[Integer.parseInt(split2[1]) * 16];
							rdPos2.read(rd2, 0, rd2.length);
							os2 = 0;

							for (int i = 0; i < Integer.parseInt(split1[1]); i++) {
								output.write(rd1, os1, 4); // 0
								output.write(rd1, os1 + 4, 4); // 4
								output.write(rd1, os1 + 8, 8); // 8
								os1 += 16;
							}
							for (int i = 0; i < Integer.parseInt(split2[1]); i++) {
								output.write(rd2, os2, 4); // 0
								output.write(rd2, os2 + 4, 4); // 4
								output.write(rd2, os2 + 8, 8); // 8
								os2 += 16;
							}
							sizeMemory += output.size();
							mergedPos.write(output.toByteArray());
							output.reset();
							lineFrom1 = r1.readLine();
							lineFrom2 = r2.readLine();
						}
					}
				}
				if (lineFrom1 == null && lineFrom2 != null) {
					String[] split = lineFrom2.split(" ", 3);
					wrVoc.write(split[0] + " " + split[1] + " " + (sizeMemory + output.size()) + "\n");
					rdPos2.seek(Long.parseLong(split[2]));
					byte[] rd2 = new byte[Integer.parseInt(split[1]) * 16];
					rdPos2.read(rd2, 0, rd2.length);
					os2 = 0;

					for (int i = 0; i < Integer.parseInt(split[1]); i++) {

						output.write(rd2, os2, 4); // 0
						output.write(rd2, os2 + 4, 4); // 4
						output.write(rd2, os2 + 8, 8); // 8
						os2 += 16;
					}
					while ((lineFrom2 = r2.readLine()) != null) {
						split = lineFrom2.split(" ", 3);
						wrVoc.write(split[0] + " " + split[1] + " " + (sizeMemory + output.size()) + "\n");

						rdPos2.seek(Long.parseLong(split[2]));
						rd2 = new byte[Integer.parseInt(split[1]) * 16];
						rdPos2.read(rd2, 0, rd2.length);
						os2 = 0;
						for (int i = 0; i < Integer.parseInt(split[1]); i++) {

							output.write(rd2, os2, 4); // 0
							output.write(rd2, os2 + 4, 4); // 4
							output.write(rd2, os2 + 8, 8); // 8
							os2 += 16;
						}
					}
				} else if (lineFrom1 != null && lineFrom2 == null) {
					String[] split = lineFrom1.split(" ", 3);
					wrVoc.write(split[0] + " " + split[1] + " " + (sizeMemory + output.size()) + "\n");
					os1 = 0;
					rdPos1.seek(Long.parseLong(split[2]));
					byte[] rd1 = new byte[Integer.parseInt(split[1]) * 16];
					rdPos1.read(rd1, 0, rd1.length);

					for (int i = 0; i < Integer.parseInt(split[1]); i++) {

						output.write(rd1, os1, 4); // 0
						output.write(rd1, os1 + 4, 4); // 4
						output.write(rd1, os1 + 8, 8); // 8
						os1 += 16;
					}
					while ((lineFrom1 = r1.readLine()) != null) {
						split = lineFrom1.split(" ", 3);
						wrVoc.write(split[0] + " " + split[1] + " " + (sizeMemory + output.size()) + "\n");

						rdPos1.seek(Long.parseLong(split[2]));
						rd1 = new byte[Integer.parseInt(split[1]) * 16];
						rdPos1.read(rd1, 0, rd1.length);
						os1 = 0;

						for (int i = 0; i < Integer.parseInt(split[1]); i++) {

							output.write(rd1, os1, 4); // 0
							output.write(rd1, os1 + 4, 4); // 4
							output.write(rd1, os1 + 8, 8); // 8
							os1 += 16;
						}
					}
				}
	//				if (id1 != 0) newId = id1 + "" + id2;
	//				else newId = id2 + "" + id1;
				System.out.println(partialIndexesQueue.toString());
				partialIndexesQueue.add(id1 + id2);
				mergedPos.write(output.toByteArray());
				rdPos1.close();
				rdPos2.close();
				r1.close();
				r2.close();
				mergedPos.close();
				wrVoc.close();
			}
			if (!dir1.getPath().equals(getIndexDirectory())) delete(dir1);
			else {
				File v = new File(dir1.getPath() + "/" + __VOCABULARY_FILENAME__);
				File p = new File(dir1.getPath() + "/" + __POSTINGS_FILENAME__);
				v.delete();
				p.delete();
			}
			delete(dir2);
		}
			// If this is the last index in the queue, we have finished merging
			// partial indexes, store all idx files to INDEX_PATH
	}

	void delete(File index)
	{
		File[] dirListings = index.listFiles();
		for(File f : dirListings)
		{
			f.delete();
		}
		index.delete();
	}

	/**
     * Method that indexes the collection that is given in the themis.config
     * file
     *
     * Used for the task of indexing!
     *
     * @return
     * @throws IOException
     */
    public boolean index() throws IOException {
        String collectionPath = __CONFIG__.getDatasetPath() ;
    	//Temporarely chancing colletion's path, in order to check if code is working.
    	//    	String collectionPath = "SemanticsScholar_2019-01-31";
        if (collectionPath != null) {
            return index(collectionPath);
        } else {
            __LOGGER__.error("DATASET_PATH not set in themis.config!");
            return false;
        }
    }


	/**
	 * Passes through documents file and saves all publication ids into memory.
	 */
	public void createNodes() throws IOException {
			RandomAccessFile __DOCUMENTS__ = new RandomAccessFile(__CONFIG__.getIndexPath() + __CONFIG__.getDocumentsFileName(),"rw");
			byte[] b = new byte[40];
			Graph graph = new Graph();
			long size = 0;
			//HashMap used to save PageRanks of each Node in memory
			HashMap<String,Pair<Double,Double>> PR = new HashMap<>(56000000);
			System.out.print("Creating nodes ");
			while(size < __DOCUMENTS__.length() - 1) {
				__DOCUMENTS__.read(b,0,40);
				Node n = new Node(new String(b));
				graph.addNode(n);
				size += 68;
				__DOCUMENTS__.seek(size);
			}
			__DOCUMENTS__.close();
			File collection = new File(__CONFIG__.getDatasetPath());
			File[] collectionsFiles = collection.listFiles();
			System.out.println(graph.getNumberOfNodes());
			System.out.println("Creating graph...");
			Double previousPR = (double)1/(graph.getPublicationCatalogue().size());
			System.out.println("Initial page rank value: " + previousPR);
			for(File f : collectionsFiles)
			{
				BufferedReader br = new BufferedReader(new FileReader(f));
				String pub;
				while((pub = br.readLine())!= null)
				{
					S2GraphEntry graphEntry = S2JsonEntryReader.readGraphEntry(pub);
					//Initializing page-rank to 1/S
					PR.put(graphEntry.getId(),new Pair(previousPR,0.0));
					List<java.lang.String> citations = graphEntry.getCitations();
					if(!citations.isEmpty()) {
						for (String s : citations) {
							if (graph.getNode(s) != null) {
								graph.addEdge(graph.getNode(graphEntry.getId()), graph.getNode(s));
							}
						}
					}
				}
				br.close();
			}
			usePageRank(graph,PR);
			writePageRank(PR);
			System.out.println("Edges: " + graph.getNumOfEdges() + " iterations#:" + pageRankIterations);
			FileWriter wr = new FileWriter(__CONFIG__.getIndexPath() + __CONFIG__.getMetaFileName(),true);
			wr.write("NUMBER OF EDGES " + graph.getNumOfEdges());
			wr.close();
			__DOCUMENTS__.close();
	}
	public void usePageRank(Graph graph,HashMap<String,Pair<Double,Double>> PR)
	{

		double maxPRchange = 0.0;
		System.out.println("New i" + "iteration");
		pageRankIterations ++;
		String changedNode = null;
		for(Node current : graph.getAdjacencyList().keySet()) {
			double pr, out = graph.getAdjacents(current).size();
			if (!graph.getAdjacents(current).isEmpty()){
				for (Node citation : graph.getAdjacents(current)) {
					pr = (PR.get(current.getId()).getL() / out) + PR.get(citation.getId()).getR();
					PR.get(citation.getId()).setR(pr);
				}
			}
		}
		System.out.print("Reseting...");
		for(String s : PR.keySet())
		{
			if(maxPRchange < (PR.get(s).getR() - PR.get(s).getL())) {
				maxPRchange = PR.get(s).getR() - PR.get(s).getL();
				maxNode = s;
			}
			if(PR.get(s).getR() != 0.0) PR.get(s).setL(PR.get(s).getR());
			PR.get(s).setR(0.0);
		}
		System.out.println(maxNode + " " + maxPRchange);
		if(maxPRchange < __CONFIG__.getPagerankThreshold()) {
			System.out.println(maxPRchange + " | " + __CONFIG__.getPagerankThreshold());
			return;
		}
		usePageRank(graph,PR);
	}

	public void writePageRank(HashMap<String, Pair<Double, Double>> PR) throws IOException {
		RandomAccessFile __DOCUMENTS__ = new RandomAccessFile(__CONFIG__.getIndexPath() + __CONFIG__.getDocumentsFileName(), "rw");
		long size = 0;
		byte[] b ;
		System.out.println("writing pageranks");
		while (size < __DOCUMENTS__.length() - 1)
		{
			b = new byte[40];
			__DOCUMENTS__.read(b,0,40);
			String s = new String(b);
			__DOCUMENTS__.seek(__DOCUMENTS__.getFilePointer() + 20);
			try {
				__DOCUMENTS__.writeDouble(PR.get(s).getL());
			}
			catch(Exception e)
			{
				System.out.println(s + " : " );
			}
			size += 68;
			__DOCUMENTS__.seek(size);
		}
		__DOCUMENTS__.close();
	}
	/**
	 * Method responsible for loading key encodings and weight offsets to memory and also opening
	 * RAF files to postings and documents, ready to seek
	 *
	 * Used for the task of querying!
	 *
	 * @return
	 * @throws IOException
	 */
	public boolean load2() throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(getIndexDirectory() + __CONFIG__.getEncodings_OffsetsFileName()));
		String b;
		
		while((b=br.readLine())!=null)
		{
			try {
				String[] split = b.split(" ", 3);
				Pair<String, Long> p = new Pair<>(split[1], Long.parseLong(split[2]));
				keyEnc_Offsets.put(Integer.parseInt(split[0]), p);
			}
			catch(Exception e)
			{
				System.out.println("b:"+b);
			}
		}
		br.close();
		return true;
	}

	/**
	 * Loads documents' pagerank in memory and returns average pagerank
	 * @return
	 * @throws IOException
	 */
	public void loadPageRank() throws IOException{
		BufferedReader __ENCODINGS__ = new BufferedReader(new FileReader(__CONFIG__.getIndexPath()+__CONFIG__.getEncodings_OffsetsFileName()));
		__DOCUMENTS__ = new RandomAccessFile(__CONFIG__.getIndexPath() + __CONFIG__.getDocumentsFileName(),"r");
		String line;
		avgPR = 0;
		while((line = __ENCODINGS__.readLine()) != null)
		{
			String[] split = line.split(" ",3);
			__DOCUMENTS__.seek(Long.parseLong(split[2]) + 60);
			double temp= __DOCUMENTS__.readDouble();
			__PAGE_RANK__.put(split[1],temp);
			avgPR += temp;
		}

		avgPR = avgPR/__PAGE_RANK__.size();
	}

	/**
	 * Loads documents' pagerank in memory and returns average pagerank
	 * @return
	 * @throws IOException
	 */
	public  HashMap<String, Double> get__PAGE_RANK__() throws IOException{
		BufferedReader __ENCODINGS__ = new BufferedReader(new FileReader(__CONFIG__.getIndexPath()+__CONFIG__.getEncodings_OffsetsFileName()));
		__DOCUMENTS__ = new RandomAccessFile(__CONFIG__.getIndexPath() + __CONFIG__.getDocumentsFileName(),"r");
		String line;
		this.avgPR = 0;
		while((line = __ENCODINGS__.readLine()) != null)
		{
			String[] split = line.split(" ",3);
			__DOCUMENTS__.seek(Long.parseLong(split[2]) + 12);
			double temp= __DOCUMENTS__.readDouble();
			__PAGE_RANK__.put(split[1],temp);
			avgPR += temp;
		}

		this.avgPR = this.avgPR/__PAGE_RANK__.size();
		return __PAGE_RANK__;
	}

	public void loadavgDL() throws NumberFormatException, IOException
	{
		BufferedReader br=new BufferedReader(new FileReader(getIndexDirectory() + __CONFIG__.getMetaFileName()));
		String b;
		while((b=br.readLine())!=null)
		{
			String[] split=b.split(" ",2);
			if(split[0].equals("avgDL:"))
			{
				avgDL = Double.parseDouble(split[1]);
				break;
			}
		}
		br.close();
	}
	/**
	 * Method responsible for loading vocabulary file to memory and also opening
	 * RAF files to postings and documents, ready to seek
	 *
	 * Used for the task of querying!
	 *
	 * @return
	 * @throws IOException
	 */
	public boolean load() throws IOException {
		String path = getIndexDirectory();

		//Buffers for reading Vocabulary,Postings File and Document File

		BufferedReader br= new BufferedReader(new FileReader(path+this.__VOCABULARY_FILENAME__)); //,1000 * 8192);//we can increase the buffer size to read faster
		__POSTINGS__=new RandomAccessFile(path+this.__POSTINGS_FILENAME__,"r");
		__DOCUMENTS__=new RandomAccessFile(path+this.__DOCUMENTS_FILENAME__,"r");
		__CONTENTS__=new RandomAccessFile(path+this.__CONTENTS_FILENAME__,"r");
	
		String vocabinfo=null;

		vocabinfo = br.readLine();
		//reading vocabulary file
		while((vocabinfo=br.readLine())!= null)
		{
			String[] VocabEntry = vocabinfo.split(" ",3);

			//first entry [0] is the String(term),second entry [1] is the DF and third entry [2] is the Long(pointer);
			//might exist a mistake and tokens become 4 because some String might be 1 mg and we tokenize at " " so there will be an extra token
			//(we separate each term in doc with spaces)
			if(VocabEntry[1] != null && VocabEntry[2] != null) {
				Pair<Integer, Long> vocab = new Pair<>(Integer.parseInt(VocabEntry[1]), Long.parseLong(VocabEntry[2]));
				this.__VOCABULARY__.put(VocabEntry[0], vocab);//insert in the vocabulary
			}
		}
		br.close();
		// Else load vocabulary file in memory in a HashMap and open
		// indexes postings and documents RAF files
		return true;
	}
	/**
     * Basic method for querying functionality. Given the list of terms in the
     * query, returns a List of Lists of DocInfoEssential objects, where each
     * list of DocInfoEssential objects holds where each list of
     * DocInfoEssential objects holds the DocInfoEssential representation of the
     * docs that the corresponding term of the query appears in. A
     * DocInfoEssential, should hold all needed information for implementing a
     * retrieval model, like VSM, Okapi-BM25, etc. This is more memory efficient
     * than holding getDocInfoFullTerms objects
     *
     * @param terms
     * @return
     */
    public List<List<DocInfoEssential>> getDocInfoEssentialForTerms(List<String> terms) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * Basic method for querying functionality. Given the list of terms in the
     * query, returns a List of Lists of DocInfoFull objects, where each list of
     * DocInfoFull objects holds the DocInfoFull representation of the docs that
     * the corresponding term of the query appears in (i.e., the whole
     * information). Not memory efficient though...
     *
     * Useful when we want to return the title, authors, etc.
     *
     * @param terms
     * @return
     */
    public List<List<DocInfoFull>> getDocInfoFullTerms(List<String> terms) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * This is a method that given a list of docs in the essential
     * representation, returns a list with the full description of docs stored
     * in the Documents File. This method is needed when we want to return the
     * full information of a list of documents. Could be useful if we support
     * pagination to the results (i.e. provide the full results of ten
     * documents)
     *
     * @param docs
     * @return
     */
    public List<DocInfoFull> getDocDescription(List<DocInfoEssential> docs) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * Method that checks if indexes have been loaded/opened
     *
     * @return
     */
    public boolean loaded() {
        return __VOCABULARY__ != null && __POSTINGS__ != null
                && __DOCUMENTS__ != null;
    }

    /**
     * Get the path of index as set in themis.config file
     *
     * @return
     */
    public String getIndexDirectory() {
        if (__CONFIG__ != null) {
            return __INDEX_PATH__;
        } else {
            __LOGGER__.error("Index has not been initialized correctly");
            return "";
        }
    }

    public HashMap<Integer,Pair<String,Long>> getkeyEnc_Offsets()
	{
		return this.keyEnc_Offsets;
	}

    public String getDatasetPath()
	{
		if(__CONFIG__ != null)
			return __CONFIG__.getDatasetPath();
		else {
			__LOGGER__.error("Dataset path has not been initialized correctly");
			return "";
		}
	}

	public TreeMap<String,ArrayList<Occurrence>> getVocab()
	{
		TreeMap<String,ArrayList<Occurrence>> v = new TreeMap<>();
		for(String term : miniVocab.keySet())
		{
			v.put(term,miniVocab.get(term));
		}
		return v;
	}

    public static HashMap<String,DocumentStructure> getDocsBatch()
	{
		return docsBatch;
	}

	/*public static HashMap<Integer,String> getKeyEncoding()
	{
		return keyEncoding;
	}*/

	public HashMap<String,ArrayList<Occurrence>> getVocabBatch() {
		return miniVocab;
	}

	public static TreeMap<String, ArrayList<FullOccurence>> getfVocab() {
		return fVocab;
	}

	public static void fVocabClear()
	{
		fVocab.clear();
	}

	public void setPostingRAF(RandomAccessFile praf)
	{
		this.__POSTINGS__=praf;
	}

	public RandomAccessFile getPostingRAF()
	{
		return this.__POSTINGS__;
	}

	public void setDocumentRAF(RandomAccessFile draf)
	{
		this.__DOCUMENTS__=draf;
	}

	public RandomAccessFile getDocumentRAF()
	{
		return this.__DOCUMENTS__;
	}

	public void setContentRAF(RandomAccessFile craf) 
	{ 
		this.__CONTENTS__=craf; 
	}
	
	public RandomAccessFile getContentRAF()
	{ 
		return this.__CONTENTS__;
	}

	public HashMap<String, Pair<Integer, Long>> getLoadedVocab()
	{
		return this.__VOCABULARY__;
	}

	public Map<String,String> getMeta()
	{
		return this.__META_INDEX_INFO__;
	}
	public Config getConfig()
	{
		return this.__CONFIG__;
	}

	public HashMap<String,Double> getPageRank()
	{
		return this.__PAGE_RANK__;
	}

	public Double getAveragePR()
	{
		return this.avgPR;
	}
}
