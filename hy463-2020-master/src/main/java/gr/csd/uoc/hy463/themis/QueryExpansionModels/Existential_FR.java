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
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
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
import java.util.HashSet;
import java.util.List;

/**
 * Implementation of the Existential retrieval model. Returns the documents that
 * contain any of the terms of the query. For this model, there is no ranking of
 * documents, since all documents that have at least one term of the query, are
 * relevant and have a score 1.0
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Existential_FR extends ARetrievalModel_FR {

	RESULT_TYPE type;
	
    public Existential_FR(Indexer index,String type) {
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
   //     throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   
    	
    	Indexer index=getIndexer();
    	
    	if(query.isEmpty() || query.get(0).getTerm().equals(""))
    	{
    		return new ArrayList<>();	
    	}
    	
    	HashMap<String,Pair<Integer,Long>> vocab=index.getLoadedVocab();
    	
    	List<Pair<Object,Double>> docs=new ArrayList<>();
    	
    	Pair<Integer,Long> termptr=null;
    	RandomAccessFile readPost=index.getPostingRAF();
    	RandomAccessFile readDoc=index.getDocumentRAF();	
    	RandomAccessFile readContents=index.getContentRAF();
    	
    	HashSet<Integer> noDuplicEntries=new HashSet<>(131072);
    	
    	int df;
    	int docId=-1;
    	int tf;
    	long pointertodoc;
    	String hashkey;
    	int toRead=0;
    	int prevDocID=-1;
    	int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB

    	
    	ByteBuffer ContentsEntry;
    	ByteBuffer DocEntry=null;
    	
    	
    	final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    	
    	
    	
    	for(QueryTerm q : query)//for each query term
    	{
    		termptr=vocab.get(q.getTerm());//get the hashmap value
    		if(termptr!=null)//if key wasnt found in map then the term doesnt exist in any document
    		{
    			Long pointer=termptr.getR();//get the pointer to postings file
    			df=termptr.getL();//get the df
    
    			//readPost.seek(pointer);//move the file pointer to the offset given
    			
    			//we create a bytebuffer to read df*12 bytes
    			ByteBuffer postList=ByteBuffer.allocate(df*16);  			
    			
    			readPost.seek(pointer);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
    			docId=-1;
    			for(int i=0;i<df;i++)
    			{
    				//prevDocID=docId;
    				docId=postList.getInt();
    				tf=postList.getInt();
    				
    				//Read the pointer to docs file
    				pointertodoc=postList.getLong();
    				if(!noDuplicEntries.contains(docId))//if the key is not in the hashmap then it is not in the list either so we add it in both of them
    				{
    					if(docId!=-1 && AllocationDoc==-1)//first entry u allocate 4032
        				{
    						readDoc.seek(pointertodoc);
            				DocEntry=ByteBuffer.allocate(4096);
            				DocEntry.clear();
            				readDoc.getChannel().read(DocEntry);
            				DocEntry.position(0);
            				AllocationDoc=docId;
        				}
        				else//not first entry which means check locality and continue
        				{
        					if((docId-AllocationDoc)<60 && docId-AllocationDoc>0)//it is in the 4KB radius
        					{
        						//move the ByteBuffer pointer at the docId location.
        						DocEntry.position((docId-(AllocationDoc))*68);//-1 because first one starts from 0 so e.g. 1 from 0 10th from 612 not 680.     						
        					}
        					else//read another 4KB in pointertoDoc position of docs file.
        					{
        						readDoc.seek(pointertodoc);
        						DocEntry.clear();
        						readDoc.getChannel().read(DocEntry);
                				DocEntry.position(0);
                				AllocationDoc=docId;
        					}
        				}
    					
    					hashkey="";
    					
    					if(type.equals(RESULT_TYPE.PLAIN))
    					{
    					
    						
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=false;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
    						{
    							Pair<Object,Double> entry=new Pair<>(hashkey,1.0);//new entry with String doc id and Double the Weight
    							noDuplicEntries.add(docId);
    							docs.add(entry);	
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
    						//DocEntry.position(DocEntry.position()+8);
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document for score calculation
    						{
	    						//properties.
	    						//create an instance of DIE<-Object
	    						DocInfoEssential DIE=new DocInfoEssential(hashkey,pointertodoc);
	    						
	    						DocEntry.position(DocEntry.position()+8);
	    						
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						
	    						DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						Pair<Object,Double> entry=new Pair<>((DocInfoEssential)DIE,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else//if it is not then move onto the next document for relevance score calculation
    						{
    							continue;
    						}
    					}
    					else//Type FULL
    					{
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document
    						{
    							
    							long pointerToContents=DocEntry.getLong();
    	    					
        						readContents.seek(pointerToContents);
        						ContentsEntry=ByteBuffer.allocate((int)readContents.readLong());
        						ContentsEntry.clear();
        						readContents.getChannel().read(ContentsEntry);
        						ContentsEntry.position(0);
    							
    							
	    						//create an instance of DIE<-Object
	    						DocInfoFull DIF = new DocInfoFull(hashkey,pointertodoc);//create an instance
	    						  
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
	   
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						
	    						Pair<Object,Double> entry=new Pair<>((DocInfoFull)DIF,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else//if it is not then move onto the next document for relevance score calculation
    						{
    							continue;
    						}
    						
    					}
    				
    					
    					
    					
    					
    					/*
    					//move file pointer to that offset
        				readDoc.seek(pointertodoc);
        				//read the size of the document entry.
        				
        				
        				//if must happen here doc fetch
        				ByteBuffer DocEntry=null;
        				DocEntry=ByteBuffer.allocate(4032);
        				
        				//NEED NOMORE
        				//int size=readDoc.readInt();
        				
    					hashkey="";
    					ByteBuffer DocEntry = null;
    					//System.out.println(hashkey);
    					if(type.equals(RESULT_TYPE.PLAIN))
    					{
    						DocEntry=ByteBuffer.allocate(40);
    						readDoc.getChannel().read(DocEntry);
    						DocEntry.position(0);
    						
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=false;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is
    						{
    							Pair<Object,Double> entry=new Pair<>(hashkey,1.0);//new entry with String doc id and Double the Weight
    							noDuplicEntries.add(docId);
    							docs.add(entry);	
    						}
    						else//else it isn't(relevant) so do not calculate the relevance score for this document. 
    						{
    							continue;
    						}
    					}
    					else if(type.equals(RESULT_TYPE.ESSENTIAL))
    					{
    					
    						DocEntry=ByteBuffer.allocate(size);
    						readDoc.getChannel().read(DocEntry);
    						DocEntry.position(0);
    						
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document for score calculation
    						{
	    						//properties.
	    						//create an instance of DIE<-Object
	    						DocInfoEssential DIE=new DocInfoEssential(hashkey,pointertodoc);
	    						toRead=DocEntry.getShort();
								DocEntry.position(toRead+DocEntry.position());	
	    						for(int j=0;j<2;j++)//skip authors,authorsID
	    						{
	    							//move the buffer pointer to current position + the Short size of the field.
	    							toRead=DocEntry.getInt();
	    							DocEntry.position(toRead+DocEntry.position());	
	    						}
	    						
	    						//skip year
	    						DocEntry.position(DocEntry.position()+2);
	    						//skip J_Name
	    						toRead=DocEntry.getShort();
	    						DocEntry.position(toRead+DocEntry.position());
	
	    						
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						
	    						DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						Pair<Object,Double> entry=new Pair<>((DocInfoEssential)DIE,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else//if it is not then move onto the next document for relevance score calculation
    						{
    							continue;
    						}
    					}
    					else//RESULT_TYPE.FULL
    					{
    						DocEntry=ByteBuffer.allocate(size);
    						readDoc.getChannel().read(DocEntry);
    						DocEntry.position(0);
    						
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document
    						{
	    						//create an instance of DIE<-Object
	    						DocInfoFull DIF = new DocInfoFull(hashkey,pointertodoc);//create an instance
	    						  
	    						//properties
	    						byte[] title=new byte[DocEntry.getShort()];
	    						DocEntry.get(title);
	    						String title2=new String(title,UTF8_CHARSET);
	    						DIF.setTitle(title2);
	    						
	    						byte[] authors=new byte[DocEntry.getInt()];
	    						DocEntry.get(authors);
	    						String authors2=new String(authors,UTF8_CHARSET);
	    						DIF.setAuthors(authors2);
	    						
	    						int aid_size=DocEntry.getInt();
	    						String aid="";
	    						for(int j=0;j<aid_size;j++)
	    							aid=aid+(char)DocEntry.get();
	    						DIF.setAuthorsID(aid);
	    						DIF.setYear(DocEntry.getShort());
	    						
	    						byte[] Journal_Name=new byte[DocEntry.getShort()];
	    						DocEntry.get(Journal_Name);
	    						String Journal_Name2=new String(Journal_Name,UTF8_CHARSET);
	    						DIF.setJournalName(Journal_Name2);
	   
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						
	    						Pair<Object,Double> entry=new Pair<>((DocInfoFull)DIF,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else//if it is not then move onto the next document for relevance score calculation
    						{
    							continue;
    						}
    					}*/
    					//clear the Documents file buffer.
    					//DocEntry.clear();
    					
    				}
    			}
    			//clear the postList Buffer.
    			postList.clear();
    		}
    	}
    	return docs;
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query,HashMap<String,Boolean> rel_docs, RESULT_TYPE type, int topk) throws IOException {
        
    	if(query.isEmpty() || query.equals(""))
    	{
    		return new ArrayList<>();	
    	}
    	
    	Indexer index=getIndexer();
    	Pair<Integer,Long> pair=null;
    	
    	HashMap<String,Pair<Integer,Long>> vocab=index.getLoadedVocab();
    	
    	List<Pair<Object,Double>> docs=new ArrayList<>();
    	
    	Pair<Integer,Long> termptr=null;
    	RandomAccessFile readPost=index.getPostingRAF();
    	RandomAccessFile readDoc=index.getDocumentRAF();
    	RandomAccessFile readContents=index.getContentRAF();
    	
    	HashSet<Integer> noDuplicEntries=new HashSet<>(131072);
    	
    	int df;
    	double idf;
    	int tf;
    	int docId;
    	long pointertodoc;
    	int Total_num_of_Docs=0;
    	String hashkey;
    	int toRead=0;
    	
    	final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    	
    	int AllocationDoc=-1;//Memory Allocation document to check if the new doc can be accessed throught the 4KB

    	
    	ByteBuffer ContentsEntry;
    	ByteBuffer DocEntry=null;
    	
    	
    	for(QueryTerm term:query)
    	{
    		pair=vocab.get(term.getTerm());
    		
    		if(pair!=null)
    		{
    			df=pair.getL();
    			idf=Math.log((double)Total_num_of_Docs/df)/Math.log(2);
    			
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
    	
    	
    	for(QueryTerm q : query)//for each query term
    	{
    		termptr=vocab.get(q.getTerm());//get the hashmap value
    		
    		if(termptr!=null)//if key wasnt found in map then the term doesnt exist in any document
    		{
    			Long pointer=termptr.getR();//get the pointer to postings file
    			df=termptr.getL();//get the df
    
    			//we create a bytebuffer to read df*12 bytes
    			ByteBuffer postList=ByteBuffer.allocate(df*16);
    			
    			readPost.seek(pointer);//move the file pointer to the offset given
    			//read the posting list of this word which the pointer points to
    			readPost.getChannel().read(postList);
    			postList.position(0);
    			
    			AllocationDoc=-1;
    			for(int i=0;i<df;i++)
    			{
    				//docId=readPost.readInt();//get the docId entry of this term
    				//readPost.skipBytes(4);//skip the tf
    				
    				docId=postList.getInt();
    				tf=postList.getInt();
    				
    				//Read the pointer to docs file
    				pointertodoc=postList.getLong();
    				
    				
    				
    				if(!noDuplicEntries.contains(docId))//if the key is not in the hashmap then it is not in the list either so we add it in both of them
    				{
    					if(docId!=-1 && AllocationDoc==-1)//first entry u allocate 4032
        				{
    						readDoc.seek(pointertodoc);
            				DocEntry=ByteBuffer.allocate(4096);
            				DocEntry.clear();
            				readDoc.getChannel().read(DocEntry);
            				DocEntry.position(0);
            				AllocationDoc=docId;
        				}
        				else//not first entry which means check locality and continue
        				{
        					if((docId-AllocationDoc)<60 && docId-AllocationDoc>0)//it is in the 4KB radius
        					{
        						//move the ByteBuffer pointer at the docId location.
        						DocEntry.position((docId-(AllocationDoc))*68);//-1 because first one starts from 0 so e.g. 1 from 0 10th from 612 not 680.     						
        					}
        					else//read another 4KB in pointertoDoc position of docs file.
        					{
        						readDoc.seek(pointertodoc);
        						DocEntry.clear();
        						readDoc.getChannel().read(DocEntry);
                				DocEntry.position(0);
                				AllocationDoc=docId;
        					}
        				}
    					hashkey="";
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
    							Pair<Object,Double> entry=new Pair<>(hashkey,1.0);//new entry with String doc id and Double the Weight
    							
    							noDuplicEntries.add(docId);
    							
    							docs.add(entry);	
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
    						//DocEntry.position(DocEntry.position()+8);
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document for score calculation
    						{
	    						//properties.
	    						//create an instance of DIE<-Object
	    						DocInfoEssential DIE=new DocInfoEssential(hashkey,pointertodoc);
	    						
	    						DocEntry.position(DocEntry.position()+8);
	    						
	    						
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						
	    						DIE.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIE.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						Pair<Object,Double> entry=new Pair<>((DocInfoEssential)DIE,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else//if it is not then move onto the next document for relevance score calculation
    						{
    							continue;
    						}
    					}
    					else//RESULT_TYPE.FULL
    					{
    						//read properties
    						for(int j=0;j<40;j++)
    						{
    							hashkey=hashkey+(char)DocEntry.get();
    						}
    						
    						
    						//checking if this document id is one of the relevant document ids that were given
    						//from the file for the evaluation
    						Boolean is_doc_rel=null;
    						is_doc_rel=rel_docs.get(hashkey);
    						if(is_doc_rel!=null && is_doc_rel==true)//if true then it is a relevant document
    						{
    							
    							long pointerToContents=DocEntry.getLong();
    	    					
        						readContents.seek(pointerToContents);
        						ContentsEntry=ByteBuffer.allocate((int)readContents.readLong());
        						ContentsEntry.clear();
        						readContents.getChannel().read(ContentsEntry);
        						ContentsEntry.position(0);
    							
    							
	    						//create an instance of DIE<-Object
	    						DocInfoFull DIF = new DocInfoFull(hashkey,pointertodoc);//create an instance
	    						  
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
	    						
	    						double weight=DocEntry.getDouble();
	    						int length=DocEntry.getInt();
	    						double PageRank=DocEntry.getDouble();
	    						DIF.setProperty(DocInfoEssential.PROPERTY.WEIGHT,(double)weight);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.LENGTH,(int)length);
	    						DIF.setProperty(DocInfoEssential.PROPERTY.PAGERANK,(double)PageRank);
	    						
	    						Pair<Object,Double> entry=new Pair<>((DocInfoFull)DIF,1.0);
								
	    						noDuplicEntries.add(docId);
	    						
	    						docs.add(entry);
    						}
    						else
    						{
    							continue;
    						}
    					}
    					//DocEntry.clear();
    				}
    				//it can be in here for 2 reasons
        			//1)we do not need to calc any weight so it can break inside the posting file while iterating df entries.
        			//2)if query size==1 we must break somewhere inside so that we return only the necessary num of docs.
        			if(docs.size()>=topk)
            		{
            			
            			break;
            		}	
    			}
    			postList.clear();
    		}
    	}
    	List<Pair<Object,Double>> sublist_DocsRetrieved=null;
    	if(docs.size()>topk)
    	{ 	
    		sublist_DocsRetrieved=docs.subList(0, topk);
    	}
    	else
    	{
    		return docs;
    	}
    	
    	return sublist_DocsRetrieved;
    }

}
