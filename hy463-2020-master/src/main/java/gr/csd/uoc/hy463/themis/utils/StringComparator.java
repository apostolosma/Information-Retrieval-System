package gr.csd.uoc.hy463.themis.utils;

import java.util.Comparator;

//Class used to sorted insertion in Tree Map.
public class StringComparator implements Comparator<String>{

	
	//Just compares two strings and returns the result.
	@Override
	public int compare(String top, String bot) {
		return top.compareTo(bot);
	}
	
}
