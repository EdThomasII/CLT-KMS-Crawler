/*
 *    Class: FindExclusionKeywords
 *   Author: Ed Thomas
 * Modified: 19-January-2016
 *  Purpose:
 *     Note: Searches for exclusionary keywords that indicate the site contains
 *           no significant relevance to forest products and/or forest products
 *           markets
 */


public class FindExclusionKeywords
{
    String Text;
    int matchCount, keywordCount;
    int matchingKeywordID[ ];
    String keyword[ ];
    
    
    public FindExclusionKeywords(String text, String [] keyword, int keywordCount) {
	this.Text = text.toUpperCase();
        this.keyword = keyword;
        this.keywordCount = keywordCount;
    } //FindExclusionKeywords constructor


    public FindExclusionKeywords() {
	this.Text = null;
        keywordCount = 0;
    } //FindExclusionKeywords empty constructor

   
    public void setText(String text) {
	this.Text = text.toUpperCase();
    }

    
    public void setKeywords(String [] keyword, int keywordCount) {
        this.keyword = keyword;
        this.keywordCount = keywordCount;
    }// setKeywords
    
    
    
    /*
     *   method: find
     *   author: Ed Thomas
     * modified: 19-January-2016
     *  returns: boolean TRUE:  if a spam or junk jeyword is found
     *                   FALSE: if clean
     */
    public boolean find() {
	int i;
        
        i = 0;
	while (i < keywordCount) { // iterate through the java resultset
            if (Text.contains(keyword[i])== true) {
                System.out.println("EXCLUSION Keyword Found: " + keyword[i]);

            	return true;
            } // if
            i++;
	} // while
	return false;
    } // find
} // class FindExclusionKeywords