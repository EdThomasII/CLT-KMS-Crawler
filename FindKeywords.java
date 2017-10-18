/*
 *    Class: FindKeywords
 *   Author: Ed Thomas
 * Modified: 27-July-2017
 *  Purpose:
 *     Note: Keywords are stored in a mysql database and regularly updated,
 *           maintained. each search session would begin by loading the 
 *           keyword set. In addition, data found could be indexed back to 
 *           and categorized by the keywords it contained.
 *
 *   Future: We could assign values to the keywords and computed a weighted 
 *           significance  for each web page visited.
 */

import java.io.*;

public class FindKeywords
{
    private static final int MAX_KEYWORDS = 100;
    private static final int MAX_SEARCH_KEYWORDS = 500;
    
    String Text;
    int matchCount, keywordCount;
    int matchingKeywordID [ ], keyID[ ];
    String keyword[ ];
    
    
    public FindKeywords(String text, String [] keyword, int [] keyID, int keywordCount) {
	this.Text = text.toUpperCase();
        this.keyword = keyword;
        this.keyID = keyID;
        this.keywordCount = keywordCount;
    } //FindKeywords constructor


    public FindKeywords() {
	this.Text = null;
        keywordCount = 0;
    } //FindKeywords empty constructor

   
    public void setText(String text) {
	this.Text = text.toUpperCase();
    }

    
    public void setKeywords(String [] keyword, int [] keyID, int keywordCount) {
        this.keyword = keyword;
        this.keyID = keyID;
        this.keywordCount = keywordCount;
    }// setKeywords
    
    
    
    /*
     *   method: find
     *   author: Ed Thomas
     * modified: 8-July-2016
     *  returns: (int) Number of matching keywords. 
     *     note: Matching keyword index numbers are stored in the class varible matchingKeyword
     */
    public int find() {
	int i;
	boolean done = false;
	this.matchCount = 0;
	this.matchingKeywordID = new int[MAX_KEYWORDS];
	
	// On the first pass, we search only for CLT specific keywords.  If none of these
	// are found, then there is no reason to search for any other keywords.

	i = 0;
	while (keyID[i] < 200 && !done && i < keywordCount) {
	    if (Text.contains(keyword[i])== true) {
		matchingKeywordID[matchCount] = keyID[i];
		matchCount++;

		if (matchCount >= MAX_KEYWORDS) {
                    done = true;
                    System.out.println(MAX_KEYWORDS + "Find: Pass 1: Maximum number of keyword hits found, search stopping");
                } // if
	    } // keyword found
	    i++;
	} // while

	if (matchCount > 0) { // ensures CLT specific keyword(s) found before performing remaining search.
	    while (i < keywordCount && !done) {
		if (Text.contains(keyword[i])== true) {
		    matchingKeywordID[matchCount] = keyID[i];
		    matchCount++;
		    
		    if (matchCount >= MAX_KEYWORDS) {
			done = true;
			System.out.println(MAX_KEYWORDS + "Find: Pass 2: Maximum number of keyword hits found, search stopping");
		    } 
		    
                } // keyword found
                i++;
	    } // while
	} // matchCount > 0
	
	return matchCount;
    } // find


    public int getKeywordMatchCount() {
	return this.matchCount;
    }
    
    public int[] getMatching() {
	return this.matchingKeywordID;
    }
} // class FindKeywords
