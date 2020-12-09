package gr.csd.uoc.hy463.themis.utils;

import java.util.ArrayList;

/**
 * This class stores all the information of a document
 * Has all the variables that we need to store in a document file.
 * Is used in a document's hashmap so we are not going to write each documennt directly in the disk.
 * @author apoma
 *
 */
public class DocumentStructure {
	private String title;
	private ArrayList<String> authorNames;
	private ArrayList<String> authorIDs;
	private short year;
	private String journalName;
	private double docsWeight;
	private int docsLength;
	private double pageRank;
	
	public DocumentStructure() {};
	public DocumentStructure(String ID,String title,ArrayList<String> authorNames,ArrayList<String> authorIDs,short year, String journalName,
								double weight,int length,double pageRank)
	{
		this.setTitle(title);
		this.setAuthorNames(authorNames);
		this.setAuthorIDs(authorIDs);
		this.setYear(year);
		this.setJournalName(journalName);
		this.setDocsWeight(weight);
		this.setDocsLength(length);
		this.setPageRank(pageRank);
	}
	
	
	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}
	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	/**
	 * @return the authorNames
	 */
	public ArrayList<String> getAuthorNames() {
		return authorNames;
	}
	/**
	 * @param authorNames the authorNames to set
	 */
	public void setAuthorNames(ArrayList<String> authorNames) {
		this.authorNames = authorNames;
	}
	/**
	 * @return the authorIDs
	 */
	public ArrayList<String> getAuthorIDs() {
		return authorIDs;
	}
	/**
	 * @param authorIDs the authorIDs to set
	 */
	public void setAuthorIDs(ArrayList<String> authorIDs) {
		this.authorIDs = authorIDs;
	}
	/**
	 * @return the year
	 */
	public short getYear() {
		return year;
	}
	/**
	 * @param year the year to set
	 */
	public void setYear(short year) {
		this.year = year;
	}
	/**
	 * @return the journalName
	 */
	public String getJournalName() {
		return journalName;
	}
	/**
	 * @param journalName the journalName to set
	 */
	public void setJournalName(String journalName) {
		this.journalName = journalName;
	}
	/**
	 * @return the docsWeight
	 */
	public double getDocsWeight() {
		return docsWeight;
	}
	/**
	 * @param docsWeight the docsWeight to set
	 */
	public void setDocsWeight(double docsWeight) {
		this.docsWeight = docsWeight;
	}
	/**
	 * @return the docsLength
	 */
	public int getDocsLength() {
		return docsLength;
	}
	/**
	 * @param docsLength the docsLength to set
	 */
	public void setDocsLength(int docsLength) {
		this.docsLength = docsLength;
	}
	/**
	 * @return the pageRank
	 */
	public double getPageRank() {
		return pageRank;
	}
	/**
	 * @param pageRank the pageRank to set
	 */
	public void setPageRank(double pageRank) {
		this.pageRank = pageRank;
	}
	
}
