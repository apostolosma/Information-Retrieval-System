package gr.csd.uoc.hy463.themis.utils;

import java.util.HashSet;
import java.util.Set;

/**
 * Class created to store occurrences.
 * Int docID => In which doc the term appeared in.
 * Int tf => term frequency in this doc.
 */
public class Occurrence {
	private int docId;
	private int tf;
	
	public Occurrence() { setTf(0);}
	public Occurrence(int docid,int num){ setDocId(docid);setTf(num);}

	
	public void setTf(int num)
	{
		this.tf = num;
	}
	
	public int getTf()
	{
		return this.tf;
	}
	
	public String toString()
	{
		return "<"+getDocId() +"," + getTf()+">";
	}

	public int getDocId() {
		return docId;
	}

	public void setDocId(int docId) {
		this.docId = docId;
	}
}
