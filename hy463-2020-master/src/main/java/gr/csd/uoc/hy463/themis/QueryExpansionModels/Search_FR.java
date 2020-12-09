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
package gr.csd.uoc.hy463.themis.QueryExpansionModels;

import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.dictionary.Dictionary;
import gr.csd.uoc.hy463.themis.QueryExpansionModels.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.shade.codehaus.stax2.ri.typed.ValueDecoderFactory.BaseArrayDecoder;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential.PROPERTY;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.metrics.themisEval;

/**
 * Some kind of simple ui to search the indexes. Some kind of GUI will be a
 * bonus!
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Search_FR {

	private static String query="";
	static List<QueryTerm> q=null;
	//The query lists to be used for evaluating the docs
	private static List<QueryTerm> vsm_AntQT=new ArrayList<>();
	private static List<QueryTerm> vsm_synQT=new ArrayList<>();
	private static List<QueryTerm> bm_AntQT=new ArrayList<>();
	private static List<QueryTerm> bm_synQT=new ArrayList<>();
	//the Title of the relevant documents
	static List<String> Title=new ArrayList<>();	
	static ARetrievalModel_FR.RESULT_TYPE a;
	
	private static HashMap<Integer,Pair<String,Long>> keyEnc_offsets;
	private static HashMap<String,Integer> map_encodings;
	

	//the 5 Synonyms to be used
	static String SynsUsed="";
	//the 5 Antonyms used
	static String AntsUsed="";
	//all the words from each doc to be used as extra
	static String TTitleUsed="";		

	static CharBuffer results;
	
	
	Dictionary dictionary=null;
	static Indexer indexer;
	
	public Search_FR()
	{
		this.map_encodings=new HashMap<>(1024);
		if(indexer.getConfig().getInfoType().equals("PLAIN"))
		{
			this.a=ARetrievalModel_FR.RESULT_TYPE.PLAIN;
		}
		else if(indexer.getConfig().getInfoType().equals("ESSENTIAL"))
		{
			this.a=ARetrievalModel_FR.RESULT_TYPE.ESSENTIAL;
		}
		else if(indexer.getConfig().getInfoType().equals("FULL"))
		{
			this.a=ARetrievalModel_FR.RESULT_TYPE.FULL;
		}
		
		keyEnc_offsets=this.indexer.getkeyEnc_Offsets();			

		//allocate 512 MB
		results=CharBuffer.allocate(1024*1024*512);
		results.clear();
	}
	
	
	public static List<QueryTerm> prepareQuery(Indexer index,String Query)
    {
		//will be used to calc tf for query and we will use hashmap and then store in arraylist to
		//aim for a complexity of hashmap,ArrayList->n+n and not arraylist->n*n.
		HashMap<String,Double> q_tf=new HashMap<>();
		Double tf;
		ArrayList<QueryTerm> Queries=new ArrayList<>();
		indexer=index;
		
		
		if(Query==null || Query.equals(""))
		{
			Queries.add(new QueryTerm("",1.0));
			return Queries;
		}
		
		Query=Query.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
		Query=Query.replaceAll("[0-9]"," ");
		Query.toLowerCase();
		setQ(Query);
		StringTokenizer queryterms = new StringTokenizer(Query," ");
		while(queryterms.hasMoreTokens())//iterate through query tokens 
		{
			String temp=queryterms.nextToken();//take next token to create a QueryTerm
			tf=0.0;
			
			//below we have the cases:stemming,stopwording,none
			//based on config we get inside in one of the cases and then we have:
			//search in map if the query term already exists(more than one apperances or not)
			//if there is no entry of this word then create one with tf=1.
			//if there is an entry then just increase the tf by one
			if(indexer.getConfig().getUseStemmer()==true && indexer.getConfig().getUseStopwords()==true)
			{
				if(!StopWords.isStopWord(temp) && !StopWords.isOpWord(temp))
				{
					String stemmed = Stemmer.Stem(temp);
					
					tf=q_tf.get(stemmed);
					if(tf==null)
					{
						q_tf.put(stemmed, 1.0);//if we want to make user input weight we need to make 1.0->user or parameter input in list with 1-1 matching
					}						   //so that e.g. : 1.0 becomes extraW
					else
					{
						tf++;//this is valid only if W of the word is initially 1.0 and not 2,3 or more...because if it is 2,3...then the weight=tf+2,3... and not +1. 
						q_tf.put(stemmed,tf);//and here we have to do tf=tf+W_NewDuplic_Entry which is the extra weight of the word given by the user.
					}
				}
			}
			else if(indexer.getConfig().getUseStemmer()==true && indexer.getConfig().getUseStopwords()==false)
			{
				String stemmed = Stemmer.Stem(temp);
				
				tf=q_tf.get(stemmed);
				if(tf==null)
				{
					q_tf.put(stemmed, 1.0);
				}
				else
				{
					tf++;
					q_tf.put(stemmed,tf);
				}
			}
			else if(indexer.getConfig().getUseStemmer()==false && indexer.getConfig().getUseStopwords()==true)
			{
				if(!StopWords.isStopWord(temp) && !StopWords.isOpWord(temp))
				{
					tf=q_tf.get(temp);
					if(tf==null)
					{
						q_tf.put(temp, 1.0);
					}
					else
					{
						tf++;
						q_tf.put(temp,tf);
					}
				}
			}
			else//none
			{
				tf=q_tf.get(temp);
				if(tf==null)
				{
					q_tf.put(temp, 1.0);
				}
				else
				{
					tf++;
					q_tf.put(temp,tf);
				}
			}
		}
		
		for(Map.Entry<String, Double> entry : q_tf.entrySet())
		{
			//query term with key->the term,value->the weight which is tf*
			//*tf:w=tf*idf<=> if query has the same word twice then tf_New=2*tf_old so w=2*(tf_old*idf) <=> so we can set as weight
			//the multiplication *2(temporarily) until we calculate the idf and get the FINAL WEIGHT.
			//THE POINT IS:if a word appears twice in a query then it has twice the weight(twice tf) than other terms which appear once(since there is no normalization).
			QueryTerm q=new QueryTerm(entry.getKey(),entry.getValue());
			Queries.add(q);
		}
		
    	return Queries;
    }

		
	public static void getTerms() throws IOException
	{
		RandomAccessFile __DOCUMENTS__=indexer.getDocumentRAF();
		RandomAccessFile __CONTENTS__=indexer.getContentRAF();
		
		if(__DOCUMENTS__==null || __CONTENTS__==null)
		{
			System.out.println("RAFS not loaded");
			System.exit(0);
		}
		
		ByteBuffer ContentsEntry;
		
		final Charset UTF8_CHARSET = Charset.forName("UTF-8");
		
		for(String hash:map_encodings.keySet())
		{
			long pos=map_encodings.get(hash);
			pos=(pos*68)+40;
			__DOCUMENTS__.seek(pos);
			long pToCon=__DOCUMENTS__.readLong();
			
			__CONTENTS__.seek(pToCon);
			ContentsEntry=ByteBuffer.allocate((int)__CONTENTS__.readLong());
			ContentsEntry.clear();
			__CONTENTS__.getChannel().read(ContentsEntry);
			ContentsEntry.position(0);
			
			byte[] title=new byte[ContentsEntry.getShort()];
			ContentsEntry.get(title);
			String title2=new String(title,UTF8_CHARSET);
			Title.add(title2);
			
		}
	}

	public static void QuerySynonyms1() throws JWNLException
	{
				
		Dictionary dictionary = Dictionary.getDefaultResourceInstance();
		int counter=0;
		Boolean found=false;
		Boolean getTWord=true;
		
		HashMap<String,Double> q_tf=new HashMap<>();
		Double tf;

		HashSet<String> duplicates=new HashSet<>();
		
		if(dictionary != null)
		{
			 MaxentTagger maxentTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
			 
			 for(String TTerms:Title)
			 {
				 String taggedQuery = maxentTagger.tagString(TTerms);
		         String[] eachTag = taggedQuery.split("\\s+");
		         
		         if(counter<5)
		        	 found=false;
		         getTWord=true;
		         for(int i = 0; i < eachTag.length; i++) {
		                String term = eachTag[i].split("_")[0];
		                String tag = eachTag[i].split("_")[1];
		                POS pos = getPos(tag);
	
		                // Ignore anything that is not a noun, verb, adjective, adverb
		                if(pos != null) {
		                	
		                    // Can get various synsets
		                    IndexWord iWord;
		                    iWord = dictionary.getIndexWord(pos, term);
		                    if(iWord !=null) 
		                    {
		                    	if(getTWord==true)
		                    	{
		                    		TTitleUsed+=term.toLowerCase()+" ";
		                    		getTWord=false;
		                    	}
		                    
		                    	if(counter<5)
		                    	{
			                    	for (Synset synset : iWord.getSenses())
			                    	{
			                            List<Word> words = synset.getWords();
			                            for (Word word : words)
			                            {
			                            	if(!word.getLemma().equals(term.toLowerCase()) && found==false  && !duplicates.contains(word.getLemma().toLowerCase()))//we want 5 Synonyms
			                            	{
								duplicates.add(word.getLemma().toLowerCase());			                            		SynsUsed+=word.getLemma()+" ";
			                            		counter++;
			                            		found=true;
			                            		break;
			                            	}

			                            }
			                            if(found==true || counter>=5)
			                            	break;
			                    	}
		                    	}
		                    	
		                   }
		                }
		                if(counter==5 && getTWord==false && found==true)
		                {
		                	break;
		                }
		         }
			 }
			 
			 //now we have SynsUsed:<- synonyms to be the Q terms.
			 //also we have TTitleUsed:<- from each title one word with meaning.
			 
			StringTokenizer SynsTerms = new StringTokenizer(SynsUsed," ");
			while(SynsTerms.hasMoreTokens())//iterate through query tokens 
		 	{
		 			String temp=SynsTerms.nextToken();//take next token to create a QueryTerm
		 			tf=0.0;
		 			
		 			if(indexer.getConfig().getUseStemmer()==true)
		 			{
		 				String stemmed = Stemmer.Stem(temp);
		 				
		 				tf=q_tf.get(stemmed);
		 				if(tf==null)
		 				{
		 					q_tf.put(stemmed, 1.0);
		 				}
		 				else
		 				{
		 					tf++;
		 					q_tf.put(stemmed,tf);
		 				}
		 			}
		 			else//none
		 			{
		 				tf=q_tf.get(temp);
		 				if(tf==null)
		 				{
		 					q_tf.put(temp, 1.0);
		 				}
		 				else
		 				{
		 					tf++;
		 					q_tf.put(temp,tf);
		 				}
		 			}
		 			
		 	}
			//TTERMS HAVE DOUBLE WEIGHT
			StringTokenizer TTerms2 = new StringTokenizer(TTitleUsed," ");
			while(TTerms2.hasMoreTokens())//iterate through query tokens 
		 	{
		 			String temp=TTerms2.nextToken();//take next token to create a QueryTerm
		 			tf=0.0;
		 			
		 			if(indexer.getConfig().getUseStemmer()==true)
		 			{
		 				String stemmed = Stemmer.Stem(temp);
		 				
		 				tf=q_tf.get(stemmed);
		 				if(tf==null)
		 				{
		 					q_tf.put(stemmed, 2.0);
		 				}
		 				else
		 				{
		 					
		 					q_tf.put(stemmed,tf+2.0);
		 				}
		 			}
		 			else//none
		 			{
		 				tf=q_tf.get(temp);
		 				if(tf==null)
		 				{
		 					q_tf.put(temp, 2.0);
		 				}
		 				else
		 				{
		 					tf++;
		 					q_tf.put(temp,tf+2.0);
		 				}
		 			}
		 			
		 	}
			
			for(Map.Entry<String, Double> entry : q_tf.entrySet())
	 		{
	 			//query term with key->the term,value->the weight which is tf*
	 			//*tf:w=tf*idf<=> if query has the same word twice then tf_New=2*tf_old so w=2*(tf_old*idf) <=> so we can set as weight
	 			//the multiplication *2(temporarily) until we calculate the idf and get the FINAL WEIGHT.
	 			//THE POINT IS:if a word appears twice in a query then it has twice the weight(twice tf) than other terms which appear once(since there is no normalization).
	 			QueryTerm q=new QueryTerm(entry.getKey(),entry.getValue());
	 			vsm_synQT.add(q);
	 		}
			bm_synQT.clear();
			bm_synQT.addAll(vsm_synQT);
			
		 }
	}

	public static void QueryAntonyms1() throws JWNLException
	{		
		Dictionary dictionary = Dictionary.getDefaultResourceInstance();
		int counter=0;
		Boolean found=false;
		
		HashMap<String,Double> q_tf=new HashMap<>();
		Double tf;
		
		HashSet<String> duplicates=new HashSet<>();

		if(dictionary != null)
		{
			 MaxentTagger maxentTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
			 
			 for(String TTerms:Title)
			 {
				 String taggedQuery = maxentTagger.tagString(TTerms);
		         String[] eachTag = taggedQuery.split("\\s+");
		         
		         if(counter<5)
		        	 found=false;
		         
		         for(int i = 0; i < eachTag.length; i++) {
		                String term = eachTag[i].split("_")[0];
		                String tag = eachTag[i].split("_")[1];
		                POS pos = getPos(tag);
	
		                // Ignore anything that is not a noun, verb, adjective, adverb
		                if(pos != null) {
		                	
		                    // Can get various synsets
		                    IndexWord iWord;
		                    iWord = dictionary.getIndexWord(pos, term);
		                    if(iWord !=null) 
		                    {
		                    	if(counter<5)
		                    	{
			                    	for (Synset synset : iWord.getSenses())
			                    	{
			                    		PointerTargetNodeList a=PointerUtils.getAntonyms(synset);
			                    		
			                    		if(!a.toString().equals("[]"))
			                    		{               		
			    	                		//System.out.println(a);
			    	                		
			    	                		List<Word> words = a.getFirst().getSynset().getWords();

			    	                		for(Word word : words)
			    	                		{
			    	                			AntsUsed+=word.getLemma().toLowerCase()+" ";
			    	                			duplicates.add(word.getLemma().toLowerCase());
									if(!duplicates.contains(a.getLast().getSynset().getWords().get(0).getLemma().toLowerCase()))
			    	                			{
			    	                				AntsUsed+=a.getLast().getSynset().getWords().get(0).getLemma().toLowerCase()+" ";
			    	                				duplicates.add(a.getLast().getSynset().getWords().get(0).getLemma().toLowerCase());
			    	                			}
			    	                			counter++;
			                            		found=true;
			                            		break;
			    	                		}
			    	                		if(found==true || counter>=5)
			    	                		{
			    	                			break;
			    	                		}
			                            }
			                         
			                    	}
		                    	}
		                    
		                    }
		                }
		                if(counter>=5 || found==true)
                    	{
                    		break;
                    	}
		         }
		         if(counter>=5 || found==true)
             	 {
             		 break;
             	 }
			 }
			 
			 //Title from other method
			 //and ants from this method
			 
			StringTokenizer AntsTerms = new StringTokenizer(AntsUsed," ");
			while(AntsTerms.hasMoreTokens())//iterate through query tokens 
			{
			 			String temp=AntsTerms.nextToken();//take next token to create a QueryTerm
			 			tf=0.0;
			 			
			 			if(indexer.getConfig().getUseStemmer()==true)
			 			{
			 				String stemmed = Stemmer.Stem(temp);
			 				
			 				tf=q_tf.get(stemmed);
			 				if(tf==null)
			 				{
			 					q_tf.put(stemmed, -1.5);
			 				}
			 				else
			 				{
			 					
			 					q_tf.put(stemmed,tf-1.5);
			 				}
			 			}
			 			else//none
			 			{
			 				tf=q_tf.get(temp);
			 				if(tf==null)
			 				{
			 					q_tf.put(temp, -1.5);
			 				}
			 				else
			 				{
			 					tf++;
			 					q_tf.put(temp,tf-1.5);
			 				}
			 			}
			 			
			 	}
			
			//TTERMS HAVE 2.5 WEIGHT because of antonyms used as reducers
			StringTokenizer TTerms2 = new StringTokenizer(TTitleUsed," ");
			while(TTerms2.hasMoreTokens())//iterate through query tokens 
		 	{
		 			String temp=TTerms2.nextToken();//take next token to create a QueryTerm
		 			tf=0.0;
		 			
		 			if(indexer.getConfig().getUseStemmer()==true)
		 			{
		 				String stemmed = Stemmer.Stem(temp);
		 				
		 				tf=q_tf.get(stemmed);
		 				if(tf==null)
		 				{
		 					q_tf.put(stemmed, 2.3);
		 				}
		 				else
		 				{
		 					
		 					q_tf.put(stemmed,tf+2.3);
		 				}
		 			}
		 			else//none
		 			{
		 				tf=q_tf.get(temp);
		 				if(tf==null)
		 				{
		 					q_tf.put(temp, 2.3);
		 				}
		 				else
		 				{
		 					tf++;
		 					q_tf.put(temp,tf+2.3);
		 				}
		 			}
		 			
		 	}
			
			for(Map.Entry<String, Double> entry : q_tf.entrySet())
	 		{
	 			//query term with key->the term,value->the weight which is tf*
	 			//*tf:w=tf*idf<=> if query has the same word twice then tf_New=2*tf_old so w=2*(tf_old*idf) <=> so we can set as weight
	 			//the multiplication *2(temporarily) until we calculate the idf and get the FINAL WEIGHT.
	 			//THE POINT IS:if a word appears twice in a query then it has twice the weight(twice tf) than other terms which appear once(since there is no normalization).
	 			QueryTerm q=new QueryTerm(entry.getKey(),entry.getValue());
	 			vsm_AntQT.add(q);
	 		}
			bm_AntQT.clear();
			bm_AntQT.addAll(vsm_AntQT);
				
			 
		}
		
	}

	public static void fill_map(HashMap<String,Boolean> relev_map)
	{
		Boolean exists=false;
		Boolean isRel=false;
		for(int i:keyEnc_offsets.keySet())
		{
			exists=relev_map.get(keyEnc_offsets.get(i).getL());
			if(exists!=null && exists==true)
			{
				map_encodings.put(keyEnc_offsets.get(i).getL(), i);
				exists=false;
			}
		}
	}
	
	
	public static List<QueryTerm> GloveSynonyms1(WordVectors model,String Query,boolean DWeight)
	{
		Indexer index=indexer;
		
		List<QueryTerm> Qtokens=new ArrayList<>();
		List<QueryTerm> QExtraTerms=new ArrayList<>();
		
		HashMap<String,Double> q_tf=new HashMap<>();
		Double tf;
		
		String ExtraTerms="";
		String tempQ;
		ArrayList<String> tokens=new ArrayList<>();
		String type;
		type=index.getConfig().getInfoType();
		
		if(Query==null || Query.equals(""))
		{
			Qtokens.add(new QueryTerm("",1.0));
			return Qtokens;
		}
		
		if(index.getConfig().getUseStopwords()==true)
		{
			tempQ=Query.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
			tempQ=tempQ.replaceAll("[0-9]"," ");
			tempQ.toLowerCase();
			
			StringTokenizer queryterms = new StringTokenizer(tempQ," ");
			while(queryterms.hasMoreTokens())//iterate through query tokens 
			{
				String temp2=queryterms.nextToken();
			
				if(!StopWords.isStopWord(temp2) && !StopWords.isOpWord(temp2))
				{
					tokens.add(temp2);
					if(indexer.getConfig().getUseStemmer()==true)
					{
						String stemmed = Stemmer.Stem(temp2);
						
						tf=q_tf.get(stemmed);
						if(tf==null)
						{						
							q_tf.put(stemmed, 1.0);
			
						}
						else
						{
							q_tf.put(stemmed, tf+1.0);
							
						}
						
					}
				}
			}
			
		}
		
		for(String t:tokens)
		{
			Collection<String> stringList = model.wordsNearest(t, 1);
			if(!stringList.isEmpty())
			{
				for(String Syn:stringList)
				{
					SynsUsed+=Syn+" ";
					if(indexer.getConfig().getUseStemmer()==true)
					{
						String stemmedSyn=Stemmer.Stem(Syn);
						tf=q_tf.get(stemmedSyn);
						if(tf==null)
						{
							if(DWeight==false)
							{
								q_tf.put(stemmedSyn, 1.0);
							}
							else
							{
								q_tf.put(stemmedSyn, 2.0);
							}
						}
						else
						{
							if(DWeight==false)
							{
								q_tf.put(stemmedSyn, tf+1.0);
							}
							else
							{
								q_tf.put(stemmedSyn, tf+2.0);
							}
						}
						
					}
				}
			}
		}
		
		for(Map.Entry<String, Double> entry : q_tf.entrySet())
		{
			//query term with key->the term,value->the weight which is tf*
			//*tf:w=tf*idf<=> if query has the same word twice then tf_New=2*tf_old so w=2*(tf_old*idf) <=> so we can set as weight
			//the multiplication *2(temporarily) until we calculate the idf and get the FINAL WEIGHT.
			//THE POINT IS:if a word appears twice in a query then it has twice the weight(twice tf) than other terms which appear once(since there is no normalization).
			QueryTerm q=new QueryTerm(entry.getKey(),entry.getValue());
			Qtokens.add(q);
		}
		
		return Qtokens;
	}	
	
	private static POS getPos(String taggedAs) {
        switch(taggedAs) {
            case "NN" :
            case "NNS" :
            case "NNP" :
            case "NNPS" :
                return POS.NOUN;
            case "VB" :
            case "VBD" :
            case "VBG" :
            case "VBN" :
            case "VBP" :
            case "VBZ" :
                return POS.VERB;
            case "JJ" :
            case "JJR" :
            case "JJS" :
                return POS.ADJECTIVE;
            case "RB" :
            case "RBR" :
            case "RBS" :
                return POS.ADVERB;
            default:
                return null;
        	}
        }
	
	public static void setQ(String Query)
	{
		query=Query;
	}
	
	public String getQ()
	{
		return this.query;
	}
	
	public static void Run_B3() throws IOException, JWNLException
	{
		
		Indexer index=indexer;
		long vsm1,vsm2,vsm3=0;
		long vsm11,vsm22,vsm33=0;
		long bm11,bm22,bm33=0;
		long bm1,bm2,bm3=0;
		
		String vsm_worstTimeQS="",bm_worstTimeQS="";
		long vsm_worstTimeS=0,bm_worstTimeS=0;
		long vsm_worstReadS=0,vsm_worstScoreS=0,vsm_worstSortS=0;
		long bm_worstReadS=0,bm_worstScoreS=0,bm_worstSortS=0;
		
		String vsm_worstTimeQA="",bm_worstTimeQA="";
		long vsm_worstTimeA=0,bm_worstTimeA=0;
		long vsm_worstReadA=0,vsm_worstScoreA=0,vsm_worstSortA=0;
		long bm_worstReadA=0,bm_worstScoreA=0,bm_worstSortA=0;
		
		String vsm_WSyns="",bm_WSyns="";
		String vsm_WAnts="",bm_WAnts="";
		String vsm_WSTitle="",bm_WSTitle="";
		String vsm_WATitle="",bm_WATitle="";
		
		long getTitlesT1,getTitlesT2;
		long getAntonymsT1,getAntonymsT2;
		long getSynonymsT1,getSynonymsT2;


		List<QueryTerm> tokens=new ArrayList<>();
		List<Pair<Object,Double>> docsRetrieved = new ArrayList<>();
		List<Pair<Object,Double>> docsRetrieved_Ant = new ArrayList<>();
		List<Pair<Object,Double>> final_score=new ArrayList<>();
		HashMap<Object,Double> Ant_docs=new HashMap<>();
		HashMap<String, Boolean> relevance_map;
		String type;
		
		int cnt=0;
		String radius="";
		type=index.getConfig().getInfoType();

		int flag=1;
		
		File QSet=new File(indexer.getConfig().getJudgmentsFileName());
		//File QSet=new File(indexer.getConfig().getQuerySet_Path()+indexer.getConfig().getQuerySet_FileName());
		//QSet.createNewFile();
		List<String> allPublications = Files.readAllLines(QSet.toPath());
		for (String publication : allPublications) 
		{
			S2TextualEntry entry = S2JsonEntryReader.readTextualEntry_QueryFile(publication);	
			String Query=entry.getQuery();
		
			tokens=prepareQuery(index,Query);
		
			relevance_map = entry.getDocumentRelevanceMap();
			map_encodings.clear();
	        fill_map(relevance_map);
			getTitlesT1=System.nanoTime();
			getTerms();
			getTitlesT2=System.nanoTime();
			getSynonymsT1=System.nanoTime();
			QuerySynonyms1();
			getSynonymsT2=System.nanoTime();
			getAntonymsT1=System.nanoTime();
			QueryAntonyms1();
			getAntonymsT2=System.nanoTime();
	        
			vsm_synQT.addAll(tokens);
			bm_synQT.addAll(tokens);
			vsm_AntQT.addAll(tokens);
			bm_AntQT.addAll(tokens);
			
			results.append("Query: "+Query+"\n");
			results.append("Info Type: "+type+"\n");
			results.append("Getting Titles time: "+(getTitlesT2-getTitlesT1));
			results.append("\nGetting Synonyms time: "+(getSynonymsT2-getSynonymsT1));
			results.append("\nGetting Antonyms time: "+(getAntonymsT2-getAntonymsT1)+"\n\n");
			

		if(flag==1)
		{
			System.out.println(vsm_synQT.size());
			System.out.println(bm_synQT.size());
			System.out.println(tokens.size());
			System.out.println(vsm_AntQT.size());
			flag=0;
		}

	        vsm1=System.nanoTime();
	        VSM_FR evaluate2=new VSM_FR(index,type);
	        docsRetrieved=evaluate2.getRankedResults(vsm_synQT,relevance_map, evaluate2.getModel());
	        vsm2=System.nanoTime();
	        vsm3=vsm2-vsm1;
			
	        if((vsm2-vsm1)>vsm_worstTimeS)
	        {
	        	vsm_worstTimeS=(vsm2-vsm1);
	        	vsm_worstTimeQS=Query;
	        	vsm_worstReadS=(evaluate2.T2-evaluate2.T1);
	        	vsm_worstScoreS=(evaluate2.sc2-evaluate2.sc1);
	        	vsm_worstSortS=(evaluate2.sort2-evaluate2.sort1);
	        	vsm_WSyns=SynsUsed;
	        	vsm_WSTitle=TTitleUsed;
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB3ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1),"Synonyms",1.0,2.0);
	        else
	        	results.append("No results\n");
	        	
	        
	        vsm11=System.nanoTime();
	        docsRetrieved_Ant=evaluate2.getRankedResults(vsm_AntQT,relevance_map, evaluate2.getModel());
	        vsm22=System.nanoTime();
	        vsm33=vsm22-vsm11;
	      
	        if((vsm22-vsm11)>vsm_worstTimeA)
	        {
	        	vsm_worstTimeA=(vsm22-vsm11);
	        	vsm_worstTimeQA=Query;
	        	vsm_worstReadA=(evaluate2.T2-evaluate2.T1);
	        	vsm_worstScoreA=(evaluate2.sc2-evaluate2.sc1);
	        	vsm_worstSortA=(evaluate2.sort2-evaluate2.sort1);
	        	vsm_WAnts=AntsUsed;
	        	vsm_WATitle=TTitleUsed;
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB3ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1),"Antonyms",-1.0,2.5);
	        else
	        	results.append("No results\n");
	        
	        bm1=System.nanoTime();
	        OkapiBM25_FR evaluate3=new OkapiBM25_FR(index,type);
	        docsRetrieved=evaluate3.getRankedResults(bm_synQT,relevance_map,evaluate3.getModel());
	        bm2=System.nanoTime();
	        bm3=bm2-bm1;
	        
	        if((bm2-bm1)>bm_worstTimeS)
	        {
	        	bm_worstTimeS=(bm2-bm1);
	        	bm_worstTimeQS=Query;
	        	bm_worstReadS=(evaluate3.T2-evaluate3.T1);
	        	bm_worstScoreS=(evaluate3.sc2-evaluate3.sc1);
	        	bm_worstSortS=(evaluate3.sort2-evaluate3.sort1);
	        	bm_WSyns=SynsUsed;
	        	bm_WSTitle=TTitleUsed;
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB3ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1),"Synonyms",1.0,2.0);
	        else
	        	results.append("No results\n");
	        
	        bm11=System.nanoTime();
	        docsRetrieved=evaluate3.getRankedResults(bm_AntQT,relevance_map,evaluate3.getModel());
	        bm22=System.nanoTime();
	        bm33=bm22-bm11;
	        
	        if((bm22-bm11)>bm_worstTimeA)
	        {
	        	bm_worstTimeA=(bm22-bm11);
	        	bm_worstTimeQA=Query;
	        	bm_worstReadA=(evaluate3.T2-evaluate3.T1);
	        	bm_worstScoreA=(evaluate3.sc2-evaluate3.sc1);
	        	bm_worstSortA=(evaluate3.sort2-evaluate3.sort1);
	        	bm_WAnts=AntsUsed;
	        	bm_WATitle=TTitleUsed;
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB3ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1),"Antonyms",-1.0,2.5);
	        else
	        	results.append("No results\n");
	        
	        
	        Title.clear();
	        vsm_synQT.clear();
	        vsm_AntQT.clear();
	        bm_synQT.clear();
	        bm_AntQT.clear();
	        
	        map_encodings.clear();
	        
	        SynsUsed="";
	        AntsUsed="";
	        TTitleUsed="";
		cnt++;
	        if(cnt%100==0)
		{
	        	dumpB3ModelsInfo();
			results.clear();
			radius="["+(cnt-100)+","+cnt+"]";
			dumpB3MetricsInfo(radius,vsm3,vsm33,bm3,bm33,vsm_worstTimeS,vsm_worstTimeA,bm_worstTimeS,bm_worstTimeA,vsm_worstTimeQS,vsm_worstTimeQA,bm_worstTimeQS,bm_worstTimeQA,vsm_worstReadS,vsm_worstReadA,bm_worstReadS,bm_worstReadA,vsm_worstScoreS,vsm_worstScoreA,bm_worstScoreS,bm_worstScoreA,vsm_worstSortS,vsm_worstSortA,bm_worstSortS,bm_worstSortA,vsm_WSyns,vsm_WAnts,bm_WSyns,bm_WAnts,vsm_WSTitle,vsm_WATitle,bm_WSTitle,bm_WATitle);
		}

		}
		dumpB3ModelsInfo();
		results.clear();
		
		radius="["+(cnt-100)+","+cnt+"]";

		dumpB3MetricsInfo(radius,vsm3,vsm33,bm3,bm33,vsm_worstTimeS,vsm_worstTimeA,bm_worstTimeS,bm_worstTimeA,vsm_worstTimeQS,vsm_worstTimeQA,bm_worstTimeQS,bm_worstTimeQA,vsm_worstReadS,vsm_worstReadA,bm_worstReadS,bm_worstReadA,vsm_worstScoreS,vsm_worstScoreA,bm_worstScoreS,bm_worstScoreA,vsm_worstSortS,vsm_worstSortA,bm_worstSortS,bm_worstSortA,vsm_WSyns,vsm_WAnts,bm_WSyns,bm_WAnts,vsm_WSTitle,vsm_WATitle,bm_WSTitle,bm_WATitle);
		
	}
	public static void Run_B2() throws IOException
	{
		Indexer index=indexer;
		
		int cnt=0;

		List<QueryTerm> tokens=new ArrayList<>();
		List<Pair<Object,Double>> docsRetrieved = new ArrayList<>();
		List<Pair<Object,Double>> docsRetrieved_Ant = new ArrayList<>();
		List<Pair<Object,Double>> final_score=new ArrayList<>();
		HashMap<Object,Double> Ant_docs=new HashMap<>();
		HashMap<String, Boolean> relevance_map;
		ArrayList<Pair<String,Boolean>> list_relevance_map;
		long vsm1,vsm2,vsm3=0;
		long bm1,bm2,bm3=0;
		String vsm_worstTimeQ="",bm_worstTimeQ="";
		long vsm_worstTime=0,bm_worstTime=0;
		long vsm_worstRead=0,vsm_worstScore=0,vsm_worstSort=0;
		long bm_worstRead=0,bm_worstScore=0,bm_worstSort=0;		


		String type;
		themisEval Stats=new themisEval();
		System.out.println(indexer.getConfig().getJudgmentsFileName());
		File QSet=new File(indexer.getConfig().getJudgmentsFileName());
		//File QSet=new File(indexer.getConfig().getQuerySet_Path()+indexer.getConfig().getQuerySet_FileName());
		List<String> allPublications = Files.readAllLines(QSet.toPath());
		for (String publication : allPublications) {
			S2TextualEntry entry = S2JsonEntryReader.readTextualEntry_QueryFile(publication);
			
			String query=entry.getQuery();
			tokens=prepareQuery(index,query);
			
			type=index.getConfig().getInfoType();
			relevance_map = entry.getDocumentRelevanceMap();
			list_relevance_map=(ArrayList<Pair<String, Boolean>>) entry.getListDocumentRelevanceMap();
			
			Stats.Calculate_Metrics(list_relevance_map, query);

			results.append("Query: "+query+"\n");
			results.append("Info Type: "+type+"\n\n");
			//System.out.println("1");
	        vsm1=System.nanoTime();
	        VSM_FR evaluate2=new VSM_FR(index,type);
	        docsRetrieved=evaluate2.getRankedResults(tokens,relevance_map, evaluate2.getModel());
	        vsm2=System.nanoTime();
	        vsm3+=vsm2-vsm1;

		if((vsm2-vsm1)>vsm_worstTime)
	        {
	        	vsm_worstTime=(vsm2-vsm1);
	        	vsm_worstTimeQ=query;
			vsm_worstRead=(evaluate2.T2-evaluate2.T1);
	        	vsm_worstScore=(evaluate2.sc2-evaluate2.sc1);
	        	vsm_worstSort=(evaluate2.sort2-evaluate2.sort1);
	        }

		//System.out.println(docsRetrieved.get(0).getR());

	        if(docsRetrieved.size()>0)
	        	AppendB2ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1));
	        else
	        	results.append("No results\n");
			docsRetrieved.clear();

		
		
			tokens=prepareQuery(index,query);
			bm1=System.nanoTime();
		     OkapiBM25_FR evaluate3=new OkapiBM25_FR(index,type);
		     docsRetrieved=evaluate3.getRankedResults(tokens,relevance_map,evaluate3.getModel());
		     bm2=System.nanoTime();
		     bm3+=bm2-bm1;
			
			if((bm2-bm1)>bm_worstTime)
		        {
		        	bm_worstTime=(bm2-bm1);
		        	bm_worstTimeQ=query;
				bm_worstRead=(evaluate3.T2-evaluate3.T1);
		        	bm_worstScore=(evaluate3.sc2-evaluate3.sc1);
		        	bm_worstSort=(evaluate3.sort2-evaluate3.sort1);
		        }

	//System.out.println(docsRetrieved.get(0).getR());

		     if(docsRetrieved.size()>0)
		    	 AppendB2ModelsInfo(docsRetrieved,"OkapiBM25",(bm2-bm1));
		     else
		        	results.append("No results\n");

			cnt++;
			if(cnt%100==0)
				System.out.println(cnt);
		}
		
		dumpB2ModelsInfo();
		results.clear();
		
		Stats.Calculate_AverageScores();
		Stats.Calculate_MeanScores();
		
		dumpB2Metrics(Stats,vsm3,bm3,vsm_worstTime,bm_worstTime,vsm_worstTimeQ,bm_worstTimeQ,vsm_worstRead,vsm_worstScore,vsm_worstSort,bm_worstRead,bm_worstScore,bm_worstSort);		
	

	}
	
	
	public static void Run_B4() throws IOException
	{
		File gloveModel = new File(indexer.getConfig().getGloveModelFileName());
        // This will take some time!
        // Wikipedia 2014 + Gigaword 5  from https://nlp.stanford.edu/projects/glove/
        WordVectors model = WordVectorSerializer.readWord2VecModel(gloveModel);
		
		
		Indexer index=indexer;
		List<Pair<Object,Double>> docsRetrieved = new ArrayList<>();
		List<QueryTerm> finalQ;
		List<QueryTerm> finalQ2;
		HashMap<String, Boolean> relevance_map;
		
		long vsm1,vsm2,vsm3=0;
		long vsm11,vsm22,vsm33=0;
		long bm1,bm2,bm3=0;
		long bm11,bm22,bm33=0;
		
		String vsm_worstTimeQ="",bm_worstTimeQ="";
		long vsm_worstTime=0,bm_worstTime=0;
		long vsm_worstRead=0,vsm_worstScore=0,vsm_worstSort=0;
		long bm_worstRead=0,bm_worstScore=0,bm_worstSort=0;
		
		String vsm_worstTimeQDW="",bm_worstTimeQDW="";
		long vsm_worstTimeDW=0,bm_worstTimeDW=0;
		long vsm_worstReadDW=0,vsm_worstScoreDW=0,vsm_worstSortDW=0;
		long bm_worstReadDW=0,bm_worstScoreDW=0,bm_worstSortDW=0;
		
		File QSet=new File(indexer.getConfig().getJudgmentsFileName());
		//File QSet=new File(indexer.getConfig().getQuerySet_Path()+indexer.getConfig().getQuerySet_FileName());
		List<String> allPublications = Files.readAllLines(QSet.toPath());
		String type=index.getConfig().getInfoType();
		
		int cnt=0;
		for (String publication : allPublications) 
		{
			S2TextualEntry entry = S2JsonEntryReader.readTextualEntry_QueryFile(publication);
						
			String Query=entry.getQuery();
			setQ(Query);
			
			results.append("Query: "+Query+"\n");
			results.append("Info Type: "+type+"\n");
			
			finalQ=GloveSynonyms1(model,Query,false);
			
			relevance_map = entry.getDocumentRelevanceMap();
	        
			vsm1=System.nanoTime();
	        VSM_FR evaluate2=new VSM_FR(index,type);
	        docsRetrieved=evaluate2.getRankedResults(finalQ,relevance_map, evaluate2.getModel());
	        vsm2=System.nanoTime();
	        vsm3+=vsm2-vsm1;
	        
	        if((vsm2-vsm1)>vsm_worstTime)
	        {
	        	vsm_worstTime=(vsm2-vsm1);
	        	vsm_worstTimeQ=Query;
	        	vsm_worstRead=(evaluate2.T2-evaluate2.T1);
	        	vsm_worstScore=(evaluate2.sc2-evaluate2.sc1);
	        	vsm_worstSort=(evaluate2.sort2-evaluate2.sort1);
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB4ModelsInfo(docsRetrieved,"VSM",(vsm2-vsm1),false);
	        else
	        	results.append("No results\n");
	        
	        SynsUsed="";
	        docsRetrieved.clear();
	        finalQ.clear();
	     
	        finalQ2=GloveSynonyms1(model,Query,true);
	        
	        vsm11=System.nanoTime();
	        docsRetrieved=evaluate2.getRankedResults(finalQ2,relevance_map, evaluate2.getModel());
	        vsm22=System.nanoTime();
	        vsm33+=vsm22-vsm11;
	        
	        if((vsm22-vsm11)>vsm_worstTimeDW)
	        {
	        	vsm_worstTimeDW=(vsm22-vsm11);
	        	vsm_worstTimeQDW=Query;
	        	vsm_worstReadDW=(evaluate2.T2-evaluate2.T1);
	        	vsm_worstScoreDW=(evaluate2.sc2-evaluate2.sc1);
	        	vsm_worstSortDW=(evaluate2.sort2-evaluate2.sort1);
	        }
	        
	        if(docsRetrieved.size()>0)
	        	AppendB4ModelsInfo(docsRetrieved,"VSM",(vsm22-vsm11),true);
	        else
	        	results.append("No results\n");
	        
	        SynsUsed="";
	        docsRetrieved.clear();
	        finalQ2.clear();
	        
	        finalQ=GloveSynonyms1(model,Query,false);
	        
	        bm1=System.nanoTime();
		    OkapiBM25_FR evaluate3=new OkapiBM25_FR(index,type);
		    docsRetrieved=evaluate3.getRankedResults(finalQ,relevance_map,evaluate3.getModel());
		    bm2=System.nanoTime();
		    bm3+=bm2-bm1;
		    
		    if((bm2-bm1)>bm_worstTime)
	        {
	        	bm_worstTime=(bm2-bm1);
	        	bm_worstTimeQ=Query;
	        	bm_worstRead=(evaluate3.T2-evaluate3.T1);
	        	bm_worstScore=(evaluate3.sc2-evaluate3.sc1);
	        	bm_worstSort=(evaluate3.sort2-evaluate3.sort1);
	        }
		    
		    if(docsRetrieved.size()>0)
		    	 AppendB4ModelsInfo(docsRetrieved,"OkapiBM25",(bm2-bm1),false);
		     else
		        	results.append("No results\n");
		    SynsUsed="";
		    docsRetrieved.clear();
		    finalQ.clear();
		    
		    finalQ2=GloveSynonyms1(model,Query,true);
		    
		    bm11=System.nanoTime();
		    docsRetrieved=evaluate3.getRankedResults(finalQ2,relevance_map,evaluate3.getModel());
		    bm22=System.nanoTime();
		    bm33+=bm22-bm11;
	        
		    if((bm22-bm11)>bm_worstTimeDW)
	        {
	        	bm_worstTimeDW=(bm22-bm11);
	        	bm_worstTimeQDW=Query;
	        	bm_worstReadDW=(evaluate3.T2-evaluate3.T1);
	        	bm_worstScoreDW=(evaluate3.sc2-evaluate3.sc1);
	        	bm_worstSortDW=(evaluate3.sort2-evaluate3.sort1);
	        }
		    
		    if(docsRetrieved.size()>0)
		    	 AppendB4ModelsInfo(docsRetrieved,"OkapiBM25",(bm22-bm11),true);
		     else
		        	results.append("No results\n");
		    SynsUsed="";
		    docsRetrieved.clear();
		    finalQ2.clear();
			
			
		    if(cnt%100==0)
		    	System.out.println(cnt);
		    
		    cnt++;
		}
		
		dumpB4ModelsInfo();
		results.clear();
		
		dumpB4Metrics(vsm3,vsm33,bm3,bm33,vsm_worstTime,vsm_worstTimeDW,bm_worstTime,bm_worstTimeDW,vsm_worstTimeQ,vsm_worstTimeQDW,bm_worstTimeQ,bm_worstTimeQDW
				,vsm_worstRead,vsm_worstReadDW,bm_worstRead,bm_worstReadDW,vsm_worstScore,vsm_worstScoreDW,bm_worstScore,bm_worstScoreDW,vsm_worstSort,vsm_worstSortDW,bm_worstSort,bm_worstSortDW);
		
		
		
	}	
	
	public static void Run_B6(Indexer index) throws IOException {
		List<QueryTerm> tokens=new ArrayList<>();
		List<Pair<Object,Double>> __DOCS_RETRIEVED__ = new ArrayList<>();
		HashMap<String, Boolean> relevance_map;
		HashMap<String,Double> __PAGE_RANK__ = null;
		ArrayList<Pair<String,Boolean>> list_relevance_map;
		long bestRead=0,bestScore=0,bestTime=0,docsLength=0;
		long bm1,bm2,bm3=0;
		long worstRead=0,worstScore=0,worstTime=0;
		String worstQ = null,bestQ = null;
		String type = null;
		int count = 0;
		System.out.println(indexer.getConfig().getJudgmentsFileName());
		File QSet=new File(indexer.getConfig().getJudgmentsFileName());
		indexer.loadPageRank();
		List<String> allPublications = Files.readAllLines(QSet.toPath());
		System.out.println("Calculating...");
		for (String publication : allPublications) {
			S2TextualEntry entry = S2JsonEntryReader.readTextualEntry_QueryFile(publication);
			String query=entry.getQuery();
			results.append("Query: " + query);

			type=index.getConfig().getInfoType();
			relevance_map = entry.getDocumentRelevanceMap();
			tokens=prepareQuery(index,query);

			bm1=System.nanoTime();
			OkapiBM25_FR evaluate3=new OkapiBM25_FR(index,type);
			__DOCS_RETRIEVED__=evaluate3.getB6RankedResults(tokens,relevance_map,evaluate3.getModel(),__PAGE_RANK__);
			bm2=System.nanoTime();
			bm3+=bm2-bm1;
			if((bm2-bm1) > worstTime)
			{
				worstQ = query;
				worstScore = evaluate3.sc2 - evaluate3.sc1;
				worstTime = bm2-bm1;
				worstRead = evaluate3.T2 - evaluate3.T1;
			}
			else if((bm2-bm1) < bestTime)
			{
				bestTime = bm2-bm1;
				bestQ = query;
				bestScore = evaluate3.sc2 - evaluate3.sc1;
				bestRead = evaluate3.T2 - evaluate3.T1;
			}
			if(bestQ == null) {bestTime = bm2-bm1;}
			appendB6models(__DOCS_RETRIEVED__,(bm2-bm1));
			docsLength += __DOCS_RETRIEVED__.size();

		}
		long avgTime = bm3/docsLength;
		System.out.println("Writing...");
		writeB6metrics(worstQ,type,worstScore,worstTime,bm3,worstRead,bestQ,bestScore,bestTime,bestRead, avgTime);
		writeB6models(type,bm3);	
	}
	
	public static void appendB6models(List<Pair<Object,Double>> __DOCS_RETRIEVED__,long time)
	{
		results.append("\nTime for query evaluation: "+time+"\n");
		results.append("Results:\n");

		for(Pair<Object,Double> printd: __DOCS_RETRIEVED__)
		{
			if(a.equals(ARetrievalModel_FR.RESULT_TYPE.PLAIN))
			{
				results.append("DocumentId: "+printd.getL()+" Score: "+printd.getR()+"\n");
			}
			else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.ESSENTIAL))
			{
				DocInfoEssential d;
				d=(DocInfoEssential)printd.getL();
				results.append("DocumentId: "+d.getId() +" Score: "+printd.getR()+" Norm: "+d.getProperty(PROPERTY.WEIGHT)+" Length: "+d.getProperty(PROPERTY.LENGTH)+
						" Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
			}
			else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.FULL))
			{
				DocInfoFull d;
				d=(DocInfoFull)printd.getL();
				results.append("DocumentId: "+d.getId() +" Score: "+printd.getR()+" Title: "+d.getTitle()+" Authors: "+d.getAuthors()+" AuthorsID: "+d.getAuthorsID()+" Year: "+d.getYear()
						+" Journal Name: "+d.getJournalName()+" Norm: "+ d.getProperty(PROPERTY.WEIGHT)+" Length: "+ d.getProperty(PROPERTY.LENGTH)+" Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
			}
		}
		results.append("\n");
	}

        public static void writeB6models(String model,long evtime) throws IOException {
		File b6f = new File(indexer.getConfig().getIndexPath() + indexer.getConfig().getB6ModelsResults_FileName());
		BufferedWriter writer = new BufferedWriter(new FileWriter(b6f,true));
		writer.write("Model: "+model+"\n");
		writer.write("Total time for evaluation: "+evtime+"\n");
		int size=results.position();
		results.rewind();
		char[] cb6=results.array();
		writer.write(cb6, 0, size);

		writer.close();
	}
	
	public static void writeB6metrics(String wrsQuery,String type,long worstScore,long worstTime,long bm3,long worstRead,String bstQuery,long bestScore
			,long bestTime,long bestRead ,long avgTime) throws IOException {
		File b6f = new File(indexer.getConfig().getIndexPath() + indexer.getConfig().getB6MetricsResults_FileName());
		BufferedWriter writer = new BufferedWriter(new FileWriter(b6f,true));

		writer.write("Type: " + type + "\n");
		writer.write("PageRank weight parameter: " + indexer.getConfig().getPagerankPublicationsWeight() + "\n");
		writer.write("Retrieval model weight parameter: " + indexer.getConfig().getRetrievalModelWeight() + "\n");
		writer.write("Average time per query: " + avgTime + "\n");
		writer.write("Best query: " + bstQuery + "\n\t-Evaluation Time: " + bestTime + "\n\t-Read time: " + bestRead + "\n\t-Score: " + bestScore +"\n");
		writer.write("Worst query: " + wrsQuery + "\n\t-Evaluation Time: " + worstTime + "\n\t-Read time: " + worstRead + "\n\t-Score: " + worstScore +"\n");
		writer.close();
	}

	public static void main (String[] args) throws ClassNotFoundException, IOException, JWNLException
	{
		Indexer index=new Indexer();
		
		index.load();
		if(!index.loaded())
		{
			System.out.println("NOT LOADED");
			return;
		}
		index.loadavgDL();
		index.load2();
		indexer=index;
		
		ARetrievalModel_FR.RESULT_TYPE a;
		
		Search_FR evaluate=new Search_FR();
		
		//evaluate.Run_B2();
		//evaluate.Run_B3();
		//evaluate.Run_B4();
		
		Run_B6(indexer);
	}


	
	public static void AppendB2ModelsInfo(List<Pair<Object,Double>> docsRetrieved,String model,long time) throws IOException
	{
		DecimalFormat df = new DecimalFormat("###.####");
		
		results.append("Model: "+model+"\n");
		results.append("Time for "+model+" evaluation: "+time+"\n");
		results.append("Results:\n");
		
		for(Pair<Object,Double> printd: docsRetrieved)
        { 
       	 if(a.equals(ARetrievalModel_FR.RESULT_TYPE.PLAIN))
	    	 {
	    		 results.append("DocumentId: "+printd.getL()+" Score: "+df.format(printd.getR())+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.ESSENTIAL))
	    	 {
	    		 DocInfoEssential d;
	    		 d=(DocInfoEssential)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Norm: "+d.getProperty(PROPERTY.WEIGHT)+" Length: "+d.getProperty(PROPERTY.LENGTH)+
	    				 " Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.FULL))
	    	 {
	    		 DocInfoFull d;
	    		 d=(DocInfoFull)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Title: "+d.getTitle()+" Authors: "+d.getAuthors()+" AuthorsID: "+d.getAuthorsID()+" Year: "+d.getYear()
	    				 	+" Journal Name: "+d.getJournalName()+" Norm: "+ d.getProperty(PROPERTY.WEIGHT)+" Length: "+ d.getProperty(PROPERTY.LENGTH)+" Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
        }
		results.append("\n");
	}
	
	public static void dumpB2ModelsInfo() throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB2ModelsResults_FileName());
		
		myObj.createNewFile();
		
		FileWriter myWriter = new FileWriter(myObj,true);
	
		int size=results.position();
		results.rewind();
		char[] CharBufB2=results.array();
		myWriter.write(CharBufB2, 0, size);
		myWriter.close();
	}
	
		
	public static void dumpB2Metrics(themisEval metric,long vsm,long bm,long vsm_wT,long bm_wT,String vsm_WTQ,String bm_WTQ,long vsm_WR,long vsm_WSc,long vsm_WS,long bm_WR,long bm_WSc,long bm_WS) throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB2MetricsResults_FileName());
		
		FileWriter myWriter = new FileWriter(myObj,true);
		
		myWriter.write("\nTime for whole set of queries for vsm evaluation: "+vsm);
		myWriter.write("\nTime for whole set of queries  for bm25 evaluation: "+bm);
		myWriter.write("\nmin AP: " + metric.getMin_AP().getL()+" doc map: "+metric.getMin_AP().getR()+" query: "+metric.getMin_AP_Q());
		myWriter.write("\nmax AP: " + metric.getMax_AP().getL()+" doc map: "+metric.getMax_AP().getR()+" query: "+metric.getMax_AP_Q());
		myWriter.write("\nmin nDCG: " + metric.getMin_nDCG().getL()+" doc map: "+metric.getMin_nDCG().getR()+" query: "+metric.getMin_nDCG_Q());
		myWriter.write("\nmax nDCG: " + metric.getMax_nDCG().getL()+" doc map: "+metric.getMax_nDCG().getR()+" query: "+metric.getMax_nDCG_Q());
		myWriter.write("\nmean AP: "+metric.getMean_AP()+"\nAverage AP: "+metric.getAv_AP());
		myWriter.write("\nmean nDCG: "+metric.getMean_nDCG()+"\nAverage nDCG: "+metric.getAv_nDCG());
		myWriter.write("\nAverage time for vsm: "+vsm/635+"\nWorst Time for vsm: "+vsm_wT+" with Query: "+vsm_WTQ);
		myWriter.write("\nWorst time for vsm Reading the info: "+vsm_WR+" Worst time for vsm calculating the scores: "+vsm_WSc+" Worst time for vsm Sorting the list: "+ vsm_WS);
		myWriter.write("\nAverage time for bm: "+bm/635+"\nWorst Time for bm: "+bm_wT+" with Query: "+bm_WTQ);		
		myWriter.write("\nWorst time for Okapi-BM25 Reading the info: "+bm_WR+" Worst time for Okapi-BM25 calculating the scores: "+bm_WSc+" Worst time for Okapi-BM25 Sorting the list: "+ bm_WS);

		myWriter.close();
	}

	public static void AppendB3ModelsInfo(List<Pair<Object,Double>> docsRetrieved,String model,long time,String feature,double weight,double Tweight)
	{

		DecimalFormat df = new DecimalFormat("###.#####");
		
		results.append("Model: "+model+"\n");
		if(feature.equals("Antonyms"))
			results.append(feature+" Used: "+AntsUsed+" with weight: "+weight+"\n");
		else
			results.append(feature+" Used: "+SynsUsed+" with weight: "+weight+"\n");
		results.append("Title terms used: "+TTitleUsed+" with weight: "+Tweight+"\n");
		results.append("Time for "+model+" evaluation: "+time+"\n");
		results.append("Results:\n");
		
		for(Pair<Object,Double> printd: docsRetrieved)
        {
       	 if(a.equals(ARetrievalModel_FR.RESULT_TYPE.PLAIN))
	    	 {
	    		 results.append("DocumentId: "+printd.getL()+" Score: "+df.format(printd.getR())+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.ESSENTIAL))
	    	 {
	    		 DocInfoEssential d;
	    		 d=(DocInfoEssential)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Norm: "+d.getProperty(PROPERTY.WEIGHT)+" Length: "+d.getProperty(PROPERTY.LENGTH)+
	    				 " Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.FULL))
	    	 {
	    		 DocInfoFull d;
	    		 d=(DocInfoFull)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Title: "+d.getTitle()+" Authors: "+d.getAuthors()+" AuthorsID: "+d.getAuthorsID()+" Year: "+d.getYear()
	    				 	+" Journal Name: "+d.getJournalName()+" Norm: "+ d.getProperty(PROPERTY.WEIGHT)+" Length: "+ d.getProperty(PROPERTY.LENGTH)+" Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
        }
		results.append("\n");
	}

	public static void dumpB3ModelsInfo() throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB3ModelsResults_FileName());
		
		myObj.createNewFile();
		
		FileWriter myWriter = new FileWriter(myObj,true);
	
		int size=results.position();
		results.rewind();
		char[] CharBufB3=results.array();
		myWriter.write(CharBufB3, 0, size);
		myWriter.close();
	}

	public static void dumpB3MetricsInfo(String radius,long vsm_Syn,long vsm_Ant,long bm_Syn,long bm_Ant,long vsm_wT_Syn,long vsm_wT_Ant,long bm_wT_Syn,long bm_wT_Ant,String vsm_WTQ_Syn,String vsm_WTQ_Ant,String bm_WTQ_Syn,String bm_WTQ_Ant,long vsm_WR_Syn,long vsm_WR_Ant,long bm_WR_Syn,long bm_WR_Ant,long vsm_WSc_Syn,long vsm_WSc_Ant,long bm_WSc_Syn,long bm_WSc_Ant,long vsm_WS_Syn,long vsm_WS_Ant,long bm_WS_Syn,long bm_WS_Ant,String vsm_WSyns,String vsm_WAnts,String bm_WSyns,String bm_WAnts,String vsm_WST,String vsm_WAT,String bm_WST,String bm_WAT) throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB3MetricsResults_FileName());
		
		FileWriter myWriter = new FileWriter(myObj,true);
		
		myWriter.write("\nRadius of queries which these info were taken from: "+radius+" of the Judgements File\n");
		
		myWriter.write("\nTime for whole set of queries for vsm evaluation with the Synonym feature: "+vsm_Syn);
		myWriter.write("\nTime for whole set of queries  for Okapi-BM25 evaluation with the Synonym feature: "+bm_Syn);
		
		myWriter.write("\nTime for whole set of queries for vsm evaluation with the Antonym feature : "+vsm_Ant);
		myWriter.write("\nTime for whole set of queries  for Okapi-BM25 evaluation with the Antonym feature: "+bm_Ant);
		
		myWriter.write("\nWith The Synonym Feature:\nAverage time for vsm: "+vsm_Syn/635+"\nWorst Time for vsm: "+vsm_wT_Syn+" with Query: "+vsm_WTQ_Syn+" with Synonyms: "+vsm_WSyns+" and worst title terms: "+vsm_WST);
		myWriter.write("\nWorst time for vsm Reading the info: "+vsm_WR_Syn+" Worst time for vsm calculating the scores: "+vsm_WSc_Syn+" Worst time for vsm Sorting the list: "+ vsm_WS_Syn);
		
		myWriter.write("\nWith The Antonym Feature:\nAverage time for vsm: "+vsm_Ant/635+"\nWorst Time for vsm: "+vsm_wT_Ant+" with Query: "+vsm_WTQ_Ant+" with Antonyms: "+vsm_WAnts+" and worst title terms: "+vsm_WAT);
		myWriter.write("\nWorst time for vsm Reading the info: "+vsm_WR_Ant+" Worst time for vsm calculating the scores: "+vsm_WSc_Ant+" Worst time for vsm Sorting the list: "+ vsm_WS_Ant);
		
		myWriter.write("\nWith the Synonym Feature:\nAverage time for Okapi-BM25: "+bm_Syn/635+"\nWorst Time for Okapi-BM25: "+bm_wT_Syn+" with Query: "+bm_WTQ_Syn+" with Synonyms: "+bm_WSyns+" and worst title terms: "+bm_WST);
		myWriter.write("\nWorst time for Okapi-BM25 Reading the info: "+bm_WR_Syn+" Worst time for Okapi-BM25 calculating the scores: "+bm_WSc_Syn+" Worst time for Okapi-BM25 Sorting the list: "+ bm_WS_Syn);
		
		myWriter.write("\nWith The Antonym Feature:\nAverage time for Okapi-BM25: "+bm_Ant/635+"\nWorst Time for Okapi-BM25: "+bm_wT_Ant+" with Query: "+bm_WTQ_Ant+" with Antonyms: "+bm_WAnts+" and worst title terms: "+bm_WAT);
		myWriter.write("\nWorst time for Okapi-BM25 Reading the info: "+bm_WR_Ant+" Worst time for Okapi-BM25 calculating the scores: "+bm_WSc_Ant+" Worst time for Okapi-BM25 Sorting the list: "+ bm_WS_Ant);
		
		myWriter.close();
	}


	public static void AppendB4ModelsInfo(List<Pair<Object,Double>> docsRetrieved,String model,long time,boolean feature)
	{
		DecimalFormat df = new DecimalFormat("###.####");
		
		results.append("Model: "+model+"\n");
		results.append("Synonyms Used: "+SynsUsed);
		results.append("\nDouble Weight Feature: "+feature);
		results.append("\nTime for "+model+" evaluation: "+time+"\n");
		results.append("Results:\n");
		
		for(Pair<Object,Double> printd: docsRetrieved)
        {
       	 if(a.equals(ARetrievalModel_FR.RESULT_TYPE.PLAIN))
	    	 {
	    		 results.append("DocumentId: "+printd.getL()+" Score: "+df.format(printd.getR())+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.ESSENTIAL))
	    	 {
	    		 DocInfoEssential d;
	    		 d=(DocInfoEssential)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Norm: "+d.getProperty(PROPERTY.WEIGHT)+" Length: "+d.getProperty(PROPERTY.LENGTH)+
	    				 " Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
	    	 else if(a.equals(ARetrievalModel_FR.RESULT_TYPE.FULL))
	    	 {
	    		 DocInfoFull d;
	    		 d=(DocInfoFull)printd.getL();
	    		 results.append("DocumentId: "+d.getId() +" Score: "+df.format(printd.getR())+" Title: "+d.getTitle()+" Authors: "+d.getAuthors()+" AuthorsID: "+d.getAuthorsID()+" Year: "+d.getYear()
	    				 	+" Journal Name: "+d.getJournalName()+" Norm: "+ d.getProperty(PROPERTY.WEIGHT)+" Length: "+ d.getProperty(PROPERTY.LENGTH)+" Pagerank: "+d.getProperty(PROPERTY.PAGERANK)+"\n");
	    	 }
        }
		results.append("\n");
	}	

	public static void dumpB4ModelsInfo() throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB4ModelsResults_FileName());
		
		myObj.createNewFile();
		
		FileWriter myWriter = new FileWriter(myObj,true);
	
		int size=results.position();
		results.rewind();
		char[] CharBufB4=results.array();
		myWriter.write(CharBufB4, 0, size);
		myWriter.close();
	}

	public static void dumpB4Metrics(long vsm,long vsmDW,long bm,long bmDW,long vsm_wT,long vsm_wTDW,long bm_wT,long bm_wTDW,String vsm_WTQ,String vsm_WTQDW,String bm_WTQ,String bm_WTQDW,long vsm_WR,long vsm_WRDW,long bm_WR,long bm_WRDW,long vsm_WSc,long vsm_WScDW,long bm_WSc,long bm_WScDW,long vsm_WS,long vsm_WSDW,long bm_WS,long bm_WSDW) throws IOException
	{
		File myObj = new File(indexer.getConfig().getResults_Path()+indexer.getConfig().getB4MetricsResults_FileName());
		
		FileWriter myWriter = new FileWriter(myObj,true);
		
		
		myWriter.write("Time for whole set of queries for vsm evaluation without the Double Weight feature: "+vsm);
		myWriter.write("\nTime for whole set of queries  for Okapi-BM25 evaluation without the Double Weight feature: "+bm);
		
		myWriter.write("\nTime for whole set of queries for vsm evaluation with the Double Weight feature : "+vsmDW);
		myWriter.write("\nTime for whole set of queries  for Okapi-BM25 evaluation with the Double Weight feature: "+bmDW);
		
		myWriter.write("\nWithout The Feature:\nAverage time for vsm: "+vsm/635+"\nWorst Time for vsm: "+vsm_wT+" with Query: "+vsm_WTQ);
		myWriter.write("\nWorst time for vsm Reading the info: "+vsm_WR+" Worst time for vsm calculating the scores: "+vsm_WSc+" Worst time for vsm Sorting the list: "+ vsm_WS);
		
		myWriter.write("\nWith The Feature:\nAverage time for vsm: "+vsmDW/635+"\nWorst Time for vsm: "+vsm_wTDW+" with Query: "+vsm_WTQDW);
		myWriter.write("\nWorst time for vsm Reading the info: "+vsm_WRDW+" Worst time for vsm calculating the scores: "+vsm_WScDW+" Worst time for vsm Sorting the list: "+ vsm_WSDW);
		
		myWriter.write("\nWithout The Feature:\nAverage time for Okapi-BM25: "+bm/635+"\nWorst Time for Okapi-BM25: "+bm_wT+" with Query: "+bm_WTQ);
		myWriter.write("\nWorst time for Okapi-BM25 Reading the info: "+bm_WR+" Worst time for Okapi-BM25 calculating the scores: "+bm_WSc+" Worst time for Okapi-BM25 Sorting the list: "+ bm_WS);
		
		myWriter.write("\nWith The Feature:\nAverage time for Okapi-BM25: "+bmDW/635+"\nWorst Time for Okapi-BM25: "+bm_wTDW+" with Query: "+bm_WTQDW);
		myWriter.write("\nWorst time for Okapi-BM25 Reading the info: "+bm_WRDW+" Worst time for Okapi-BM25 calculating the scores: "+bm_WScDW+" Worst time for Okapi-BM25 Sorting the list: "+ bm_WSDW);
		
		myWriter.close();
	}
}
