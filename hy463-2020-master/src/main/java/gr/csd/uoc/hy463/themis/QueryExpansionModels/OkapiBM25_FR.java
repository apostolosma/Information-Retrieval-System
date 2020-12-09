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
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel.RESULT_TYPE;
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
public class OkapiBM25_FR extends ARetrievalModel_FR {

	public double k1=2.0;
	public double b=0.75;
	public double avgDL;
	RESULT_TYPE type;

	public long T1=0,T2=0;
	public long sc1=0,sc2=0;
	public long sort1=0,sort2=0;
	
    public OkapiBM25_FR(Indexer index,String type) {
        super(index);
        this.avgDL=index.avgDL;
        
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
    	
    	//Must take k1,b from config or somewhere else
    	//they are usually 2.0 and 0.75 but we should let it be "free" based on.. the user??
    	
    	//Object:The Document
    	//HashMap : The query terms that are both inside the Query and the Doc,and the weight(double) for each DocID for this term. 
    	HashMap<Object,HashMap<String,Double>> docs_WVals=new HashMap<>(131072);
    	//HashMap That will be used to hold the inside part of the hashmap to store the info(a temporary).
    	HashMap<String,Double> temp=new HashMap<>();
    	
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
    	
    	DocInfoEssential DIE = null;
    	DocInfoFull DIF = null;
    	
    	double score=0;
    	int df=0;
    	Long pToPost=null;
    	Long pToDoc=null;
    	int docId=0;
    	int tf=0;
    	double idf=0;
    	int doc_len=0;
    	
    	String hashkey;
    	int toRead=0;
    	int Total_num_of_Docs=index.getkeyEnc_Offsets().size();
    	//double avgDL=0.0;
    	double word_score=0.0;
    	final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    	
    	int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB
    	ByteBuffer DocEntry = null;
    	ByteBuffer ContentsEntry;
    	
	T1=System.nanoTime();
    	for(QueryTerm term: query)//for each term in the Query
    	{
    		
    		pair=Vocab.get(term.getTerm());//get the pair of the vocab with key->term 
    		
    		if(pair!=null)//if pair >>isn't<< null then the term exists in some document.
    		{
    			/*We need the <df from the Vocab> and <the tf from postings for each doc>*/
    			df=pair.getL();
    			pToPost=pair.getR();
    			
    			//idf calc
    			idf=(Math.log((double)(Total_num_of_Docs-df+0.5)/(double)(df+0.5)))/(Math.log(2));//we apply the formula to calculate the word score which later shall be
    			//endof idf_c

    			ByteBuffer postList=ByteBuffer.allocate(df*16);
    			
    			readPost.seek(pToPost);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
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
    				
    				//this is the docID->hashKey 40 Bytes.
    				hashkey="";
    				
					//based on the type we need to retrieve different info but for all we need to retrieve doc length
					//to calculate the score with okapiBM25.
					if(type.equals(RESULT_TYPE.PLAIN))
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
							//skip the pointer to contents
							DocEntry.position(DocEntry.position()+8);
					
							//skip the norm
							DocEntry.position(DocEntry.position()+8);
							//read length of doc.
							doc_len=DocEntry.getInt();
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
							DIE=new DocInfoEssential(hashkey,pToDoc);
							//properties.
							DocEntry.position(DocEntry.position()+8);
							
							double docweight=DocEntry.getDouble();
							doc_len=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
							DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}
					}
					else if(type.equals(RESULT_TYPE.FULL))
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
							doc_len=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
							DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}
					}
				
					word_score=idf*((tf*(k1+1))/(tf+(k1*(1-b+(b*(doc_len/this.avgDL))))));//used to calculate the sum of all word_scores to get the final score
					if(type.equals(RESULT_TYPE.PLAIN))
					{
						temp=docs_WVals.get(hashkey);
						if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
						{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocID,hash<term,weight>>
    					docs_WVals.put(hashkey,temp);
					}
					else if(type.equals(RESULT_TYPE.ESSENTIAL))
					{
					
						//the hashmap inside for docID with <q_term,weight>
    					temp=docs_WVals.get(DIE);//get the hashmap of query terms in doc->Object type->DocInfoEssential
    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
    					{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one :D
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
    					docs_WVals.put((DocInfoEssential) DIE,temp);
						
					}
					else if(type.equals(RESULT_TYPE.FULL))
					{
						
						//the hashmap inside for docID with <q_term,weight>
    					temp=docs_WVals.get(DIF);//get the hashmap of query terms in doc->Object type->DocInfoEssential
    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
    					{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one :D
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
    					docs_WVals.put((DocInfoFull) DIF,temp);
					}
					//DocEntry.clear();
    			}
    			postList.clear();
    		}
    	}
    	T2=System.nanoTime();
    	//Calculation of the final(total) score of each document for the query
    	
    	sc1=System.nanoTime();
    	//iterate through objects<-(String/DocInfoEssential/DocInfoFull)
    	//for each object which represents a document get the hashmap(value) which contains the String<-Term,Double<-score for this Term
    	//add every term's score for this document and get it's final score.
    	//Insert it in the List with <Object,Double> which are the Document and the Score
    	for(Map.Entry<Object,HashMap<String,Double>> entry : docs_WVals.entrySet())
    	{
    		temp=entry.getValue();
    		score=0.0;
    		for(Map.Entry<String, Double> term : temp.entrySet())
    		{
    			score=score+term.getValue();
    		}
    		
    		Pair<Object,Double> doc_s=new Pair<>(entry.getKey(),score);
    		docsRetrieved.add(doc_s);
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
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query,HashMap<String,Boolean> rel_docs, RESULT_TYPE type, int topk) throws IOException {
       
    	if(query.isEmpty() || query.get(0).getTerm().equals(""))
    	{
    		return new ArrayList<>();	
    	}
    	
    	//Must take k1,b from config or somewhere else
    	//they are usually 2.0 and 0.75 but we should let it be "free" based on.. the user??
    	
    	//Object:The Document
    	//HashMap : The query terms that are both inside the Query and the Doc,and the weight(double) for each DocID for this term. 
    	HashMap<Object,HashMap<String,Double>> docs_WVals=new HashMap<>(131072);
    	//HashMap That will be used to hold the inside part of the hashmap to store the info(a temporary).
    	HashMap<String,Double> temp=new HashMap<>();
    	
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
    	
    	DocInfoEssential DIE = null;
    	DocInfoFull DIF = null;
    	
    	double score=0;
    	int df=0;
    	Long pToPost=null;
    	Long pToDoc=null;
    	int docId=0;
    	int tf=0;
    	double idf=0;
    	int doc_len=0;
    	String hashkey;
    	int toRead=0;
    	
    	int Total_num_of_Docs=index.getkeyEnc_Offsets().size();
    	//double avgDL=0.0;
    	double word_score=0.0;
    	
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
    			idf=(Math.log((double)(Total_num_of_Docs-df+0.5)/(double)(df+0.5)))/(Math.log(2));
    			
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
    	
    	
    	for(QueryTerm term: query)//for each term in the Query
    	{
    		
    		pair=Vocab.get(term.getTerm());//get the pair of the vocab with key->term 
    		
    		if(pair!=null)//if pair >>isn't<< null then the term exists in some document.
    		{
    			/*We need the <df from the Vocab> and <the tf from postings for each doc>*/
    			df=pair.getL();
    			pToPost=pair.getR();
    			
    			//idf calculation
    			
    			idf=(Math.log((double)(Total_num_of_Docs-df+0.5)/(double)(df+0.5)))/(Math.log(2));//we apply the formula to calculate the word score which later shall be
    			
    			//endof idf_c
    			

    			ByteBuffer postList=ByteBuffer.allocate(df*16);
    			
    			readPost.seek(pToPost);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
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
    				
    				//this is the docID->hashKey 40 Bytes.
    				hashkey="";
    				
					//based on the type we need to retrieve different info but for all we need to retrieve doc length
					//to calculate the score with okapiBM25.
					if(type.equals(RESULT_TYPE.PLAIN))
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
							//skip the pointer to contents
							DocEntry.position(DocEntry.position()+8);
					
							//skip the norm
							DocEntry.position(DocEntry.position()+8);
							//read length of doc.
							doc_len=DocEntry.getInt();
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
							DIE=new DocInfoEssential(hashkey,pToDoc);
							//properties.
							DocEntry.position(DocEntry.position()+8);
							
							double docweight=DocEntry.getDouble();
							doc_len=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
							DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}
					}
					else if(type.equals(RESULT_TYPE.FULL))
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
							doc_len=DocEntry.getInt();
							double PageRank=DocEntry.getDouble();
							DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
							DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
							DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
						}
						else//else it isn't(relevant) so do not calculate the relevance score for this document.
						{
							continue;
						}
						
					}
					
					word_score=idf*((tf*(k1+1))/(tf+(k1*(1-b+(b*(doc_len/this.avgDL))))));//used to calculate the sum of all word_scores to get the final score
					
					if(type.equals(RESULT_TYPE.PLAIN))
					{
						temp=docs_WVals.get(hashkey);
						if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
						{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocID,hash<term,weight>>
    					docs_WVals.put(hashkey,temp);
					}
					else if(type.equals(RESULT_TYPE.ESSENTIAL))
					{
					
						//the hashmap inside for docID with <q_term,weight>
    					temp=docs_WVals.get(DIE);//get the hashmap of query terms in doc->Object type->DocInfoEssential
    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
    					{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one :D
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
    					docs_WVals.put((DocInfoEssential) DIE,temp);
						
					}
					else if(type.equals(RESULT_TYPE.FULL))
					{
						
						//the hashmap inside for docID with <q_term,weight>
    					temp=docs_WVals.get(DIF);//get the hashmap of query terms in doc->Object type->DocInfoEssential
    					if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
    					{
    						temp.put(term.getTerm(), word_score);
    					}
    					else//ELSE if there isnt one...THEN just create one :D
    					{
    						temp=new HashMap<>();
    						temp.put(term.getTerm(), word_score);
    					}
    					//insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
    					docs_WVals.put((DocInfoFull) DIF,temp);
					}
					//DocEntry.clear();
					
					//if we have completed the list of the topk docs then we get out of the posting file and then outside of the
					//query iteration.This will cause some error in the final score but it is cost that we are determined to allow
					//so that we can make the query evaluation even faster.
    			}
    			postList.clear();
    		}
    		
    		//here we get outside the loop that iterates the query terms.
    		if(docs_WVals.size()>=topk)
			{
				break;
			}
    		
    	}
    	
    	
    	//Calculation of the final(total) score of each document for the query
    	
    	
    	//iterate through objects<-(String/DocInfoEssential/DocInfoFull)
    	//for each object which represents a document get the hashmap(value) which contains the String<-Term,Double<-score for this Term
    	//add every term's score for this document and get it's final score.
    	//Insert it in the List with <Object,Double> which are the Document and the Score
    	for(Map.Entry<Object,HashMap<String,Double>> entry : docs_WVals.entrySet())
    	{
    		temp=entry.getValue();
    		score=0.0;
    		for(Map.Entry<String, Double> term : temp.entrySet())
    		{
    			score=score+term.getValue();
    		}
    		
    		Pair<Object,Double> doc_s=new Pair<>(entry.getKey(),score);
    		docsRetrieved.add(doc_s);
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

 public List<Pair<Object, Double>> getB6RankedResults(List<QueryTerm> query,HashMap<String,Boolean> rel_docs,RESULT_TYPE type,HashMap<String,Double> __PAGE_RANK__) throws IOException
 {
	 int count = 0;
	 if(query.isEmpty() || query.get(0).getTerm().equals(""))
	 {
		 return new ArrayList<>();
	 }

	 //Must take k1,b from config or somewhere else
	 //they are usually 2.0 and 0.75 but we should let it be "free" based on.. the user??

	 //Object:The Document
	 //HashMap : The query terms that are both inside the Query and the Doc,and the weight(double) for each DocID for this term.
	 HashMap<Object,HashMap<String,Double>> docs_WVals=new HashMap<>(131072);
	 //HashMap That will be used to hold the inside part of the hashmap to store the info(a temporary).
	 HashMap<String,Double> temp=new HashMap<>();

	 Indexer index=getIndexer();

	 //the loaded vocabulary from the memory is taken here
	 HashMap<String,Pair<Integer,Long>> Vocab=index.getLoadedVocab();

	 //the final list with pairs of Object,Double
	 //Object:Based on the type(PLAIN,ESSENTIAL,FULL) the object is either String,DocIE,DocIF
	 //Double is the score of the document for this query
	 List<Pair<Object,Double>> docsRetrieved=new ArrayList<>();//final ordered list of objects.
	 HashMap<Object,String> keyValues = new HashMap<Object,String>();
	 //used to iterate hashmap for given query_term(takes the value of type Pair)
	 Pair<Integer,Long> pair=null;

	 //Streams to read from PostingFile and DocumentsFile.
	 RandomAccessFile readPost=index.getPostingRAF();
	 RandomAccessFile readDoc=index.getDocumentRAF();
	 RandomAccessFile readContents=index.getContentRAF();

	 DocInfoEssential DIE = null;
	 DocInfoFull DIF = null;

	 double score=0;
	 int df=0;
	 Long pToPost=null;
	 Long pToDoc=null;
	 int docId=0;
	 int tf=0;
	 double idf=0;
	 int doc_len=0;

	 String hashkey;
	 int toRead=0;
	 int Total_num_of_Docs=index.getkeyEnc_Offsets().size();
	 double avgScore=0.0;
	 double word_score=0.0;
	 final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	 int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB
	 ByteBuffer DocEntry = null;
	 ByteBuffer ContentsEntry;

	 T1=System.nanoTime();
	 for(QueryTerm term: query)//for each term in the Query
	 {
		 pair=Vocab.get(term.getTerm());//get the pair of the vocab with key->term

		 if(pair!=null)//if pair >>isn't<< null then the term exists in some document.
		 {
			 /*We need the <df from the Vocab> and <the tf from postings for each doc>*/
			 df=pair.getL();
			 pToPost=pair.getR();

			 //idf calc
			 idf=(Math.log((double)(Total_num_of_Docs-df+0.5)/(double)(df+0.5)))/(Math.log(2));//we apply the formula to calculate the word score which later shall be
			 //endof idf_c

			 ByteBuffer postList=ByteBuffer.allocate(df*16);

			 readPost.seek(pToPost);//move the file pointer to the offset given
			 //read the posting list of this word which the pointer points to
			 readPost.getChannel().read(postList);
			 postList.position(0);

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

				 //this is the docID->hashKey 40 Bytes.
				 hashkey="";

				 //based on the type we need to retrieve different info but for all we need to retrieve doc length
				 //to calculate the score with okapiBM25.
				 if(type.equals(RESULT_TYPE.PLAIN))
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
						 //skip the pointer to contents
						 DocEntry.position(DocEntry.position()+8);

						 //skip the norm
						 DocEntry.position(DocEntry.position()+8);
						 //read length of doc.
						 doc_len=DocEntry.getInt();
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
						 DIE=new DocInfoEssential(hashkey,pToDoc);
						 keyValues.put(DIE,hashkey);
						 //properties.
						 DocEntry.position(DocEntry.position()+8);

						 double docweight=DocEntry.getDouble();
						 doc_len=DocEntry.getInt();
						 double PageRank=DocEntry.getDouble();
						 DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
						 DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
						 DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
					 }
					 else//else it isn't(relevant) so do not calculate the relevance score for this document.
					 {
						 continue;
					 }
				 }
				 else if(type.equals(RESULT_TYPE.FULL))
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
						 keyValues.put(DIF,hashkey);

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
						 doc_len=DocEntry.getInt();
						 double PageRank=DocEntry.getDouble();
						 DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)docweight);
						 DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)doc_len);
						 DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
					 }
					 else//else it isn't(relevant) so do not calculate the relevance score for this document.
					 {
						 continue;
					 }
				 }

				 word_score=idf*((tf*(k1+1))/(tf+(k1*(1-b+(b*(doc_len/this.avgDL))))));//used to calculate the sum of all word_scores to get the final score
				 if(type.equals(RESULT_TYPE.PLAIN))
				 {
					 temp=docs_WVals.get(hashkey);
					 if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
					 {
						 temp.put(term.getTerm(), word_score);
					 }
					 else//ELSE if there isnt one...THEN just create one
					 {
						 temp=new HashMap<>();
						 temp.put(term.getTerm(), word_score);
					 }
					 avgScore += word_score;
					 //insert the temp in the final hashmap with <DocID,hash<term,weight>>
					 docs_WVals.put(hashkey,temp);
				 }
				 else if(type.equals(RESULT_TYPE.ESSENTIAL))
				 {

					 //the hashmap inside for docID with <q_term,weight>
					 temp=docs_WVals.get(DIE);//get the hashmap of query terms in doc->Object type->DocInfoEssential
					 if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
					 {
						 temp.put(term.getTerm(), word_score);
					 }
					 else//ELSE if there isnt one...THEN just create one :D
					 {
						 temp=new HashMap<>();
						 temp.put(term.getTerm(), word_score);
					 }
					 avgScore += word_score;
					 //insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
					 docs_WVals.put((DocInfoEssential) DIE,temp);

				 }
				 else if(type.equals(RESULT_TYPE.FULL))
				 {

					 //the hashmap inside for docID with <q_term,weight>
					 temp=docs_WVals.get(DIF);//get the hashmap of query terms in doc->Object type->DocInfoEssential
					 if(temp!=null)//if there is an entry with this doc id...then just insert the new one term,weight
					 {
						 temp.put(term.getTerm(), word_score);
					 }
					 else//ELSE if there isnt one...THEN just create one :D
					 {
						 temp=new HashMap<>();
						 temp.put(term.getTerm(), word_score);
					 }
					 avgScore += word_score;
					 //insert the temp in the final hashmap with <DocInfoEssential,hash<term,weight>>
					 docs_WVals.put((DocInfoFull) DIF,temp);
				 }
				 //DocEntry.clear();
			 }
			 postList.clear();
		 }
	 }
	 T2=System.nanoTime();
	 avgScore = (avgScore / docsRetrieved.size());
	 //Calculation of the final(total) score of each document for the query

	 sc1=System.nanoTime();
	 //iterate through objects<-(String/DocInfoEssential/DocInfoFull)
	 //for each object which represents a document get the hashmap(value) which contains the String<-Term,Double<-score for this Term
	 //add every term's score for this document and get it's final score.
	 //Insert it in the List with <Object,Double> which are the Document and the Score
	 for(Map.Entry<Object,HashMap<String,Double>> entry : docs_WVals.entrySet())
	 {
		 temp=entry.getValue();
		 score=0.0;
		 for(Map.Entry<String, Double> term : temp.entrySet())
		 {
			if(!type.equals(RESULT_TYPE.PLAIN)){
			 score=score + (index.getConfig().getRetrievalModelWeight() *(term.getValue()/(Math.sqrt(Math.pow(index.getAveragePR(),2) + Math.pow(avgScore,2)))))
					 + ((index.getConfig().getPagerankPublicationsWeight()) * (index.getPageRank().get(keyValues.get(entry.getKey())))/(Math.sqrt(Math.pow(index.getAveragePR(),2) + Math.pow(avgScore,2))));
		 	}
			else {
					score=score + (index.getConfig().getRetrievalModelWeight() *(term.getValue()/(Math.sqrt(Math.pow(index.getAveragePR(),2) + Math.pow(avgScore,2)))))
					 + ((index.getConfig().getPagerankPublicationsWeight()) * (index.getPageRank().get(entry.getKey()))/(Math.sqrt(Math.pow(index.getAveragePR(),2) + Math.pow(avgScore,2))));

			}
		}

		 Pair<Object,Double> doc_s=new Pair<>(entry.getKey(),score);
		 docsRetrieved.add(doc_s);
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
 }

}
