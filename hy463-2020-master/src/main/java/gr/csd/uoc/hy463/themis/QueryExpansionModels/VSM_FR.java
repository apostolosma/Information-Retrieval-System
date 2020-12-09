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

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential.PROPERTY;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;

import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the OkapiBM25 retrieval model
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class VSM_FR extends ARetrievalModel_FR {

	
	RESULT_TYPE type;//=RESULT_TYPE.ESSENTIAL;
	public long T1=0,T2=0;
	public long sc1=0,sc2=0;
	public long sort1=0,sort2=0;
	
    public VSM_FR(Indexer index,String type) {
        super(index);
        if(type.equals("PLAIN"))
        {
       	   this.type=RESULT_TYPE.PLAIN;
        }
        else if(type.equals("ESSENTIAL"))
        {
        	 this.type=RESULT_TYPE.ESSENTIAL;
        }
        else if(type.equals("FULL"))
        {
        	 this.type=RESULT_TYPE.FULL;
        }
    }
    
    public RESULT_TYPE getModel()
    {
    	return this.type;
    }
    
    public void setModel(String type)
    {
    	this.type=RESULT_TYPE.valueOf(type);
    }
    
    
    
    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query,HashMap<String,Boolean> rel_docs,RESULT_TYPE type) throws IOException {
    	
    	if(query.isEmpty() || query.get(0).getTerm().equals(""))
    	{
    		return new ArrayList<>();	
    	}
    	
    	
    	//QueryTerm:consists of->Term,Weight.
    	//Term: one of the terms in the query
    	//Weight:the weight of this query_term.
    	    	
    	//Object:The Document
    	//HashMap : The query terms that are both inside the Query and the Doc,and the weight(double) for each DocID for this term. 
    	HashMap<Object,HashMap<String,Double>> docs_WVals=new HashMap<>(131072);
    	//HashMap That will be used to hold the inside part of the hashmap to store the info(a temporary).
    	HashMap<String,Double> temp=new HashMap<>();
    	
    	//This map will hold the doc Norm to avoid reseeking over and over for the same document.
    	HashMap<String,Double> docNorm=new HashMap<>(131072);
    	
    	Indexer index=getIndexer();
    	
    	//the loaded vocabulary from the memory is taken here
    	HashMap<String,Pair<Integer,Long>> Vocab=index.getLoadedVocab();
    	
    	//the final list with pairs of Object,Double
    	//Object:Based on the type(PLAIN,ESSENTIAL,FULL) the object is either String,DocIE,DocIF
    	//Double is the score of the document for this query
    	List<Pair<Object,Double>> docsRetrieved=new ArrayList<>();//final ordered list of objects.
    	
    	//used to iterate hashmap for given query_term(takes the value of type Pair)
    	Pair<Integer,Long> pair=null;
    	
    	//Streams to read from PostingFile and DocumentsFile.
    	RandomAccessFile readPost=index.getPostingRAF();
    	RandomAccessFile readDoc=index.getDocumentRAF();
    	RandomAccessFile readContents=index.getContentRAF();
    	
    	DocInfoEssential DIE=null;
    	DocInfoFull DIF=null;
    	
  
    	double score=0;
    	int df=0;
    	Long pToPost=null;
    	Long pToDoc=null;
    	int docId=0;
    	int tf=0;
    	double idf=0;
    	double d_weight;
    	double q_weight;
    	double numerator;
    	double q_denominator;
    	double d_denominator;
    	double denominator;
    	
    	String hashkey;
    	int toRead=0;
    	int Total_num_of_Docs=index.getkeyEnc_Offsets().size();//MUST BE INITIALIZED->from indexed collection->can hold a counter.
    	final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    	
    	boolean reseek=true;
    	
    	
    	int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB
    	ByteBuffer DocEntry = null;
    	ByteBuffer ContentsEntry;
    	
    	T1=System.nanoTime();
    	//in this loop we take every query term and we search the word in the vocabulary.
    	//if found then the docs that contain this term have their tf*idf calculated
    	//and inserted in the hashmap so that later we can calculate their score with the query
    	for(QueryTerm term: query)//for each term in the Query
    	{
    		
    		pair=Vocab.get(term.getTerm());//get the pair of the vocab with key->term 
    		
    		if(pair!=null)//if pair >>isn't<< null then the term exists in some document.
    		{
    			/*We need the <df from the Vocab> and <the tf from postings for each doc>*/
    			df=pair.getL();
    			//System.out.println(df);
    			pToPost=pair.getR();
    			
    			
    			//now we need to calc tf*idf
    			
    			//here we calc the idf.
				idf=Math.log((double)Total_num_of_Docs/df)/Math.log(2.0);
    			
    			
    			//QUERY WEIGHT CALC.
    			
    			//we calculate q_w here because if we put it inside for loop every time the weight will be multiplied by an additional idf
    			q_weight=term.getWeight()*idf;
    			term.setWeight(q_weight);//so now every QueryTerm has the final weight of the query term.

    			ByteBuffer postList=ByteBuffer.allocate(df*16);
    			postList.clear();
    			
    			readPost.seek(pToPost);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
    			AllocationDoc=-1;
    			for(int i=0;i<df;i++)//for df(num of) documents read the tf,and info
    			{
    				docId=postList.getInt();
    				tf=postList.getInt();
    				
    				//Read the pointer to docs file
    				pToDoc=postList.getLong();

    				if(docId!=-1 && AllocationDoc==-1)//first entry u allocate 4032
    				{
						readDoc.seek(pToDoc);
        				DocEntry=ByteBuffer.allocate(4096);
        				DocEntry.clear();
        				readDoc.getChannel().read(DocEntry);
        				DocEntry.position(0);
        				AllocationDoc=docId;
    				}
    				else//not first entry which means check locality and continue
    				{
    					if((docId-AllocationDoc)<60 && docId-AllocationDoc>0 && AllocationDoc!=-1)//it is in the 4KB radius
    					{
    						//move the ByteBuffer pointer at the docId location.
    						DocEntry.position((docId-(AllocationDoc))*68);//-1 because first one starts from 0 so e.g. 1 from 0 10th from 612 not 680.     						
    					}
    					else//read another 4KB in pointertoDoc position of docs file.
    					{
    						readDoc.seek(pToDoc);
    						DocEntry.clear();
    						readDoc.getChannel().read(DocEntry);
            				DocEntry.position(0);
            				AllocationDoc=docId;
    					}
    				}
    				
					hashkey="";
    				
    				d_weight=tf*idf;//weight of word in doc
    				
    				if(type.equals(RESULT_TYPE.PLAIN))//return a list of stringDocID,ScoreFromVSM ranked.
    				{
						
						for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}
						//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
							
							DocEntry.position(DocEntry.position()+8);
							
							double docweight=DocEntry.getDouble();			
							docNorm.put(hashkey, docweight);	
							
							//DocEntry.position(DocEntry.position()+12);
							
							
	    					//now we need to store the hash <term,<docId,weight>>
							
	    					//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(hashkey);//get the hashmap of query terms in doc->hashkey
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocID,hash<term,weight>>
	    					docs_WVals.put(hashkey,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}	
    				}
    				else if(type.equals(RESULT_TYPE.ESSENTIAL))
    				{
    					for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}
    					//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
	    					//now we need to retrieve the essential info(of doc) from docs file.
	    					//and store the hash of <term,<docIE,weight>>
	    					
	    					//properties.
							//create an instance of DIE<-Object
	    					DIE=new DocInfoEssential(hashkey,pToDoc);
	    					
	    					DocEntry.position(DocEntry.position()+8);
	    					
							
							double docweight=DocEntry.getDouble();
							//docNorm.put(hashkey,docweight);//not needed bc of type DIE
							int length=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
							DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    					
							//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(DIE);//get the hashmap of query terms in doc->Object type->DocInfoEssential
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one :D
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
	    					docs_WVals.put((DocInfoEssential) DIE,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}
    				}
    				else//Full type
    				{
    					for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}

						//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
							
							long pointerToContents=DocEntry.getLong();
	    					
    						readContents.seek(pointerToContents);
    						ContentsEntry=ByteBuffer.allocate((int)readContents.readLong());
    						ContentsEntry.clear();
    						readContents.getChannel().read(ContentsEntry);
    						ContentsEntry.position(0);
							
							
							//create an instance of DIE<-Object
							DIF = new DocInfoFull(hashkey,pToDoc);//create an instance
							  
							//properties
							byte[] title=new byte[ContentsEntry.getShort()];
    						ContentsEntry.get(title);
    						String title2=new String(title,UTF8_CHARSET);
    						DIF.setTitle(title2);
    						
    						byte[] authors=new byte[ContentsEntry.getInt()];
    						ContentsEntry.get(authors);
    						String authors2=new String(authors,UTF8_CHARSET);
    						DIF.setAuthors(authors2);
    						
    						int aid_size=ContentsEntry.getInt();
    						String aid="";
    						for(int j=0;j<aid_size;j++)
    							aid=aid+(char)ContentsEntry.get();
    						DIF.setAuthorsID(aid);
    						DIF.setYear(ContentsEntry.getShort());
    						
    						byte[] Journal_Name=new byte[ContentsEntry.getShort()];
    						ContentsEntry.get(Journal_Name);
    						String Journal_Name2=new String(Journal_Name,UTF8_CHARSET);
    						DIF.setJournalName(Journal_Name2);
							
							double docweight=DocEntry.getDouble();
							//docNorm.put(hashkey, docweight);
							int length=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
							DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
							
							//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(DIF);//get the hashmap of query terms in doc->Object type->DocInfoEssential
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one :D
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
	    					docs_WVals.put((DocInfoFull) DIF,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}

    				}
    				//DocEntry.clear();
    			}
    			postList.clear();
    		}
    	}
    	T2=System.nanoTime();
	
    	//Calculation of the score.
    	
    	//iterate through the hashmap of docs,for each doc get the values
    	//for each value(hashmap) iterate the query terms and get the weights of the terms in each document.
    	//do the same for the same pair of words for the query.Apply the math formula to get the final score.
    	sc1=System.nanoTime();
    	for(Map.Entry<Object,HashMap<String,Double>> doc_weights: docs_WVals.entrySet())
    	{
    		//Get the q_terms found in this doc,the weights of each q_term in this doc(<-Object)
    		temp=doc_weights.getValue();
    		//System.out.println(temp);
    		score=0.0;
    		d_denominator=0.0;
    		q_denominator=0.0;
    		numerator=0.0;
    		denominator=0.0;
    		
    		if(type.equals(RESULT_TYPE.PLAIN))
    		{
    			d_denominator=docNorm.get((String)doc_weights.getKey());
    		}
    		else if(type.equals(RESULT_TYPE.ESSENTIAL))
    		{
    			DocInfoEssential doc=(DocInfoEssential)doc_weights.getKey();
    			d_denominator=(Double)doc.getProperty(PROPERTY.WEIGHT);
    		}
    		else if(type.equals(RESULT_TYPE.FULL))
    		{
    			DocInfoFull doc=(DocInfoFull)doc_weights.getKey();
    			d_denominator=(Double)doc.getProperty(PROPERTY.WEIGHT);
    		}
    		//d_denominator=d_denominator*d_denominator;	
    		
    		for(QueryTerm qterm:query)//for each query term
    		{
    			//find the query terms that appear in the map of docs and get the weight			
    			
    			if(!temp.containsKey(qterm.getTerm()))
    			{
    				d_weight=0;
    			}
    			else
    			{
    				d_weight=temp.get(qterm.getTerm());
    			}
    			q_weight=qterm.getWeight();
    			
    			numerator=numerator+(d_weight*q_weight);
    			//System.out.println(numerator);
    			//d_denominator=d_denominator+(d_weight * d_weight);
    			q_denominator=q_denominator+(q_weight * q_weight);
    			
    		}
    		
    		if(numerator==0 || d_denominator==0 || q_denominator==0)
    		{
    			score=0;
    		}
    		else
    		{
    			denominator=Math.sqrt(d_denominator*q_denominator);
    			score=numerator/denominator;
    		}
    		
    		//System.out.println(score);
    		Pair<Object,Double> doc = null;
    		if(type.equals(RESULT_TYPE.PLAIN))
    		{
    			doc=new Pair<>(doc_weights.getKey(),score);
    		}
    		else if(type.equals(RESULT_TYPE.ESSENTIAL))
    		{
    			doc=new Pair<>((DocInfoEssential)doc_weights.getKey(),score);	
    		}
    		else if(type.equals(RESULT_TYPE.FULL))
    		{
    			doc=new Pair<>((DocInfoFull)doc_weights.getKey(),score);
    		}
    		//Pair<Object,Double> doc=new Pair<>((DocInfoEssential)doc_weights.getKey(),score);
    		docsRetrieved.add(doc);
    	}
	sc2=System.nanoTime();

    	sort1=System.nanoTime();    	

    	Collections.sort(docsRetrieved,new Comparator() {
    		
    		public int compare(Object s1,Object s2)
    		{
    			Double sc1=((Pair<String,Double>) s1).getR();
    			Double sc2=((Pair<String,Double>) s2).getR();
    			
    			return sc2.compareTo(sc1);
    		}
    	});
    	sort2=System.nanoTime();
    	
    	return (ArrayList)docsRetrieved;
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query,HashMap<String,Boolean> rel_docs, RESULT_TYPE type, int topk) throws IOException {
      
    	if(query.isEmpty() || query.get(0).getTerm().equals(""))
    	{
    		return new ArrayList<>();	
    	}
    	
    	//Need to sort the list based on the df and then based on the tf of each query term
    	//Then we need to process the weight
    	//We also need to calc how many docs we currently have so that we keep searching or stop searching topk docs.
    	//finally after reaching topk docs we can stop processing queries for more documents
    	
    	//Object:The Document
    	//HashMap : The query terms that are both inside the Query and the Doc,and the weight(double) for each DocID for this term. 
    	HashMap<Object,HashMap<String,Double>> docs_WVals=new HashMap<>(topk);
    	//HashMap That will be used to hold the inside part of the hashmap to store the info(a temporary).
    	HashMap<String,Double> temp=new HashMap<>();
    	
    	//This map will hold the doc Norm to avoid reseeking over and over for the same document.
    	HashMap<String,Double> docNorm=new HashMap<>(131072);
    	
    	//the final list with pairs of Object,Double
    	//Object:Based on the type(PLAIN,ESSENTIAL,FULL) the object is either String,DocIE,DocIF
    	//Double is the score of the document for this query
    	List<Pair<Object,Double>> docsRetrieved=new ArrayList<>();//final ordered list of objects.	
    	
    	Indexer index=getIndexer();
    	
    	HashMap<String,Pair<Integer,Long>> Vocab=index.getLoadedVocab();
    	Pair<Integer,Long> pair=null;
  
    	//Streams to read from PostingFile and DocumentsFile.
    	RandomAccessFile readPost=index.getPostingRAF();
    	RandomAccessFile readDoc=index.getDocumentRAF();
    	RandomAccessFile readContents=index.getContentRAF();
    	
    	DocInfoEssential DIE=null;
    	DocInfoFull DIF=null;
    	
  
    	double score=0;
    	int df=0;
    	Long pToPost=null;
    	Long pToDoc=null;
    	int docId=0;
    	int tf=0;
    	double idf=0;
    	double d_weight;
    	double q_weight;
    	double numerator;
    	double q_denominator;
    	double d_denominator;
    	double denominator;
    	
    	String hashkey;
    	int toRead=0;
    	int Total_num_of_Docs=index.getkeyEnc_Offsets().size();
    	final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    	
    	int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB
    	ByteBuffer DocEntry = null;
    	ByteBuffer ContentsEntry;
    	
    	//iterate through all terms and then for each term
    	//get the word in the vocabulary to get the DF.
    	//Then compute the "partial" weight(idf*k) and 
    	//store it in the query term(so that we can sort the list based on the weight of the term).
    	for(QueryTerm term:query)
    	{
    		pair=Vocab.get(term.getTerm());
    		
    		if(pair!=null)
    		{
    			df=pair.getL();
    			idf=Math.log((double)Total_num_of_Docs/df)/Math.log(2);
    			//System.out.println(term.getTerm()+" " +df);
    			
    			term.setWeight(term.getWeight()*idf);//we have now computed the w=idf*k part of the weight where >k< is the initial weight
    												 //of the word.
    		}
    	}
    	
    	
    	//now we sort the query list based on the "partial" weight=idf*k in descending order.
    	Collections.sort(query,new Comparator() {
    		
    		public int compare(Object s1,Object s2)
    		{
    			Double sc1=((QueryTerm) s1).getWeight();
    			Double sc2=((QueryTerm) s2).getWeight();
    			
    			return sc2.compareTo(sc1);
    		}
    	});
    	
    	
    	//now we need to collect topk docs.
    	//and check the tf part to sort the docs that we will take.
    	
    	for(QueryTerm term: query)
    	{
    		pair=Vocab.get(term.getTerm());//get the pair of the vocab with key->term 
    		if(pair!=null)//if pair >>isn't<< null then the term exists in some document.
    		{
    			/*We need the <df from the Vocab> and <the tf from postings for each doc>*/
    			df=pair.getL();
    			pToPost=pair.getR();  
    			//System.out.println(term.getTerm());

    			//now we need to calc tf*idf
    			
    			//here we calc the idf.
				idf=Math.log((double)Total_num_of_Docs/df)/Math.log(2.0);
    		
				
				ByteBuffer postList=ByteBuffer.allocate(df*16);
    			
    			readPost.seek(pToPost);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
    			AllocationDoc=-1;
    			for(int i=0;i<df;i++)//for df(num of) documents read the tf,and info
    			{
    				docId=postList.getInt();
    				tf=postList.getInt();
    				
    				//Read the pointer to docs file
    				pToDoc=postList.getLong();
    				
    				if(docId!=-1 && AllocationDoc==-1)//first entry u allocate 4032
    				{
						readDoc.seek(pToDoc);
        				DocEntry=ByteBuffer.allocate(4096);
        				DocEntry.clear();
        				readDoc.getChannel().read(DocEntry);
        				DocEntry.position(0);
        				AllocationDoc=docId;
    				}
    				else//not first entry which means check locality and continue
    				{
    					if((docId-AllocationDoc)<60 && docId-AllocationDoc>0 && AllocationDoc!=-1)//it is in the 4KB radius
    					{
    						//move the ByteBuffer pointer at the docId location.
    						DocEntry.position((docId-(AllocationDoc))*68);//-1 because first one starts from 0 so e.g. 1 from 0 10th from 612 not 680.     						
    					}
    					else//read another 4KB in pointertoDoc position of docs file.
    					{
    						readDoc.seek(pToDoc);
    						DocEntry.clear();
    						readDoc.getChannel().read(DocEntry);
            				DocEntry.position(0);
            				AllocationDoc=docId;
    					}
    				}
    				
					hashkey="";
    				
    				d_weight=tf*idf;//weight of word in doc

    				
    				//prepei na balw to hash key sto prwto meros me to term k to weight
    				if(type.equals(RESULT_TYPE.PLAIN))//return a list of stringDocID,ScoreFromVSM ranked.
    				{
    					
    					for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}
						//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
							
							DocEntry.position(DocEntry.position()+8);
							
							double docweight=DocEntry.getDouble();			
							docNorm.put(hashkey, docweight);	
							
							
	    					//now we need to store the hash <term,<docId,weight>>
	    					
	    					//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(hashkey);//get the hashmap of query terms in doc->hashkey
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocID,hash<term,weight>>
	    					docs_WVals.put(hashkey,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}	
    					
    				}
    				else if(type.equals(RESULT_TYPE.ESSENTIAL))
    				{
						
    					for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}
    					//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
	    					//now we need to retrieve the essential info(of doc) from docs file.
	    					//and store the hash of <term,<docIE,weight>>
	    					
	    					//properties.
							//create an instance of DIE<-Object
	    					DIE=new DocInfoEssential(hashkey,pToDoc);
	    					
	    					DocEntry.position(DocEntry.position()+8);
							
							double docweight=DocEntry.getDouble();
							int length=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
							DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    					
							//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(DIE);//get the hashmap of query terms in doc->Object type->DocInfoEssential
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one :D
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
	    					docs_WVals.put((DocInfoEssential) DIE,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}	
    					
    				}
    				else if(type.equals(RESULT_TYPE.FULL))
    				{
    					
    					//now we need to retrieve the doc info(all) from docs file
    					//and store the hash of <term,<docIF,weight>>
    					
    					//read properties
    					for(int j=0;j<40;j++)
						{
							hashkey=hashkey+(char)DocEntry.get();
						}

						//checking if this document id is one of the relevant document ids that were given
						//from the file for the evaluation
						Boolean is_doc_rel=null;
						is_doc_rel=rel_docs.get(hashkey);
						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
						{
							
							long pointerToContents=DocEntry.getLong();
	    					
    						readContents.seek(pointerToContents);
    						ContentsEntry=ByteBuffer.allocate((int)readContents.readLong());
    						ContentsEntry.clear();
    						readContents.getChannel().read(ContentsEntry);
    						ContentsEntry.position(0);
							
							
							//create an instance of DIE<-Object
							DIF = new DocInfoFull(hashkey,pToDoc);//create an instance
							  
							//properties
							byte[] title=new byte[ContentsEntry.getShort()];
    						ContentsEntry.get(title);
    						String title2=new String(title,UTF8_CHARSET);
    						DIF.setTitle(title2);
    						
    						byte[] authors=new byte[ContentsEntry.getInt()];
    						ContentsEntry.get(authors);
    						String authors2=new String(authors,UTF8_CHARSET);
    						DIF.setAuthors(authors2);
    						
    						int aid_size=ContentsEntry.getInt();
    						String aid="";
    						for(int j=0;j<aid_size;j++)
    							aid=aid+(char)ContentsEntry.get();
    						DIF.setAuthorsID(aid);
    						DIF.setYear(ContentsEntry.getShort());
    						
    						byte[] Journal_Name=new byte[ContentsEntry.getShort()];
    						ContentsEntry.get(Journal_Name);
    						String Journal_Name2=new String(Journal_Name,UTF8_CHARSET);
    						DIF.setJournalName(Journal_Name2);
							
							double docweight=DocEntry.getDouble();
							int length=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
							DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
							
							//the hashmap inside for docID with <q_term,weight>
	    					temp=docs_WVals.get(DIF);//get the hashmap of query terms in doc->Object type->DocInfoEssential
	    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
	    					{
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					else//ELSE if there isnt one...THEN just create one :D
	    					{
	    						temp=new HashMap<>();
	    						temp.put(term.getTerm(), d_weight);
	    					}
	    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
	    					docs_WVals.put((DocInfoFull) DIF,temp);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}	
    				}
    				//DocEntry.clear();
    			}
    			postList.clear();
    		}
    		else//if the pair doesnt exist(in the vocab)then it means that the term does not exist in any documents.So,score is 0.
    		{
    			//score?=0 for docs so W_d_i * W_q_i =0
    		}
    		if(docs_WVals.size()>=topk)
    		{
    			break;
    		}
    	}
    	
    	
    	int counter=0;
    	for(Map.Entry<Object,HashMap<String,Double>> doc_weights: docs_WVals.entrySet())
    	{
    		//Get the q_terms found in this doc,the weights of each q_term in this doc(<-Object)
    		temp=doc_weights.getValue();
    		//System.out.println(temp);
    		score=0.0;
    		d_denominator=0.0;
    		q_denominator=0.0;
    		numerator=0.0;
    		denominator=0.0;
    		
    		if(type.equals(RESULT_TYPE.PLAIN))
    		{
    			d_denominator=docNorm.get((String)doc_weights.getKey());
    		}
    		else if(type.equals(RESULT_TYPE.ESSENTIAL))
    		{
    			DocInfoEssential doc=(DocInfoEssential)doc_weights.getKey();
    			d_denominator=docNorm.get(doc.getId());
    		}
    		else if(type.equals(RESULT_TYPE.FULL))
    		{
    			DocInfoFull doc=(DocInfoFull)doc_weights.getKey();
    			d_denominator=docNorm.get(doc.getId());
    		}
    		//d_denominator=d_denominator*d_denominator;	
    		
    		for(QueryTerm qterm:query)//for each query term
    		{
    			//find the query terms that appear in the map of docs and get the weight
    		
    			if(!temp.containsKey(qterm.getTerm()))
    			{
    				d_weight=0;
    			}
    			else
    			{
    				d_weight=temp.get(qterm.getTerm());
    			}
    			q_weight=qterm.getWeight();
   
    			
    			numerator=numerator+(d_weight*q_weight);			
    			//d_denominator=d_denominator+(d_weight * d_weight);
    			q_denominator=q_denominator+(q_weight * q_weight);
    			
    		}
    		
    		if(numerator==0 || d_denominator==0 || q_denominator==0)
    		{
    			score=0;
    		}
    		else
    		{
    			denominator=Math.sqrt(d_denominator*q_denominator);
    			score=numerator/denominator;
    		}
   
    		Pair<Object,Double> doc = null;
    		if(type.equals(RESULT_TYPE.PLAIN))
    		{
    			doc=new Pair<>(doc_weights.getKey(),score);
    		}
    		else if(type.equals(RESULT_TYPE.ESSENTIAL))
    		{
    			doc=new Pair<>((DocInfoEssential)doc_weights.getKey(),score);	
    		}
    		else if(type.equals(RESULT_TYPE.FULL))
    		{
    			doc=new Pair<>((DocInfoFull)doc_weights.getKey(),score);
    		}
    		//Pair<Object,Double> doc=new Pair<>((DocInfoEssential)doc_weights.getKey(),score);
    		
    		docsRetrieved.add(doc);
    		
    	}
    	
    	Collections.sort(docsRetrieved,new Comparator() {
    		
    		public int compare(Object s1,Object s2)
    		{
    			Double sc1=((Pair<String,Double>) s1).getR();
    			Double sc2=((Pair<String,Double>) s2).getR();
    			
    			return sc2.compareTo(sc1);
    		}
    	});
    	
    	
    	
    	List<Pair<Object,Double>> sublist_DocsRetrieved=null;
    	if(docsRetrieved.size()>topk)
    	{ 	
    		sublist_DocsRetrieved=docsRetrieved.subList(0, topk);
    	}
    	else
    	{
    		return docsRetrieved;
    	}
    	
    	return sublist_DocsRetrieved;
    		
    }

}
