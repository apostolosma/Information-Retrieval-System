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
package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * This class could be used when we want to get all information of a specific
 * document, etc. title, authors, etc. by reading the appropriate entry in the
 * documents file
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class DocInfoFull extends DocInfoEssential {

	String title;
	String Authors;
	String AuthorsID;
	Short Year;
	String JournalName;
	
	public void setTitle(String Title)
	{
		this.title=Title;
	}
	
	public String getTitle()
	{
		return this.title;
	}
	
	public void setAuthors(String auth)
	{
		this.Authors=auth;
	}
	
	public String getAuthors()
	{
		return this.Authors;
	}
	public void setAuthorsID(String authid)
	{
		this.AuthorsID=authid;
	}
	
	public String getAuthorsID()
	{
		return this.AuthorsID;
	}
	public void setJournalName(String JourN)
	{
		this.JournalName=JourN;
	}
	
	public String getJournalName()
	{
		return this.JournalName;
	}
	
	public void setYear(short year)
	{
		this.Year=year;
	}
	
	public short getYear()
	{
		return this.Year;
	}
	
    public DocInfoFull(String id, long offset) {
        super(id, offset);
    }

}
