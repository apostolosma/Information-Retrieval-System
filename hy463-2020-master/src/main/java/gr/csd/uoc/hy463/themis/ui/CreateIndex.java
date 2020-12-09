/**
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
package gr.csd.uoc.hy463.themis.ui;


import gr.csd.uoc.hy463.themis.indexer.Indexer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;

/**
 *
 * This class runs the indexers
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 *
 */
public class CreateIndex {
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		File dir = new File("./src/main/resources/s2-corpus-000-splitted-10000/xaf");
		Indexer ind = new Indexer();
//		BufferedReader reader = new BufferedReader(new FileReader(dir.getPath()));
//		S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(reader.readLine());
//		System.out.println(entry.getTitle());
//
//		ind.A1(dir);
//		System.out.println(ind.getfVocab().toString());
//		ind.fVocabClear();
//		ind.A2(dir);
//
//		for(String key : ind.getfVocab().keySet())
//		{
//			System.out.println(key + ": " +ind.getfVocab().get(key).toString());
//		}
		System.out.println("It is wroking fine");
		long st = System.nanoTime();
//		ind.index();
//		Index ind1 = new Index(ind.getConfig());
//		ind1.calculateWeights(ind);
		/*This is B5*/
		ind.createNodes(); 
		long et = System.nanoTime();
		System.out.println(et-st);
	}
	
}
