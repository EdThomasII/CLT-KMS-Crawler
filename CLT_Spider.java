/*
 *   Program: CLT_Spider
 *    Author: Ed Thomas
 *  Modified: 17-October-2017
 *      Uses: FindKeywords (to search web documents for keywords)
 *            JSoup to handle http/html requests and parsing.
 *            MySQL (java.sql.*) for database
 * 
 *     BootStrap instructions to get CLT-Spider system working. Note, the Download directory 
 *                 contents should be deleted whenever the database is re-initialized.
 *            1) Follow instructions in mysql-Notes.txt to create the SpiderRoot mysql account
 *            2) run the CLT-Spider-Revised.mysql script to create/initialize the database
 *                       mysql -u root -p <CLT-Spider-Revised.mysql
 *               OR, load the database from backup
 *                       mysql -u root -p <CLT-SQL-Backup.mysql 
 *            3) compile the CLT-Spider 
 *                       javac CLT_Spider.java
 *            4) seed the database with startup links
 *                       java CLT_Spider file CLT-Engineering.html
 *                       java CLT_Spider file CLT-Pine.html
 *                       java CLT_Spider file hardwood-CLT-Google-Search.html
 *            5) Start crawling to build/expand the database
 *                       java CLT-Spider crawl
 *
 *  Good Seed web site
 *   https://www.fpl.fs.fed.us/products/publications/featured_pubs.php
 *
 *  links below return a moved permanent status (HTTP 301):
 *    http://www.bcfii.ca/sites/default/files/2014-15-FPInnovations-Fire-Resistance-of-Long-Span-Composite-Wood-Concrete-Floor-Systems.pdf
 *    http://www.bcfii.ca/sites/default/files/fii413-summary-2014-15-fpinnovations-fire-performance-of-clt-panel-to-panel-joints.pdf
 *
 *
 *     Immediate Needs:
 *            1) Implement a check to track duplication of publications among sites and 
 *               use it help weigh the comparitive value of the publication.
 *            2) Need method of handling re-direction of pdf of other information resources. 
 *            3) Need Method to build synopsis for a url based on the url's doc file.
 *            4) Verify code that handles resource file re-direction and connection timeouts is
 *               operating correctly.
 * 
 *     Long Term Needs:
 *            1) processURL implements only the explore mode -- creating new links/url. Need to 
 *               implement code that will update existing information or delete links that no
 *               longer exist. 
 *            2) Create webpage and suitable address for sysadmins to inquire/report behavior of 
 *               of the CLT spider.  The link mentioned in the code is: http://bbe.umn.edu/bot.html. 
 *               I made this link up for the testing phase, a real link should be put on the 
 *               UMN website. 
 *            3) Need software to categorize links. This would allow us to better match the 
 *               information our users are searching for. 
 *            4) Keep track of number of "Junk links", ie: 0 hit links within a domain.  Once it hits
 *               a predetermined number, 10 or 20?, we mark the domain as low yield and stop probing  
 *               the domain for additional information. For example, I crawled the congress.gov
 *               website, it is huge and has only sparse information related to CLT. 
 */

import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.sql.*;
import java.lang.String.*;

//import java.net.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.MalformedURLException;

import java.util.Calendar;

import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;


/*
 *    Class: CLT_Spider
 * Modified: 24-July-2016
 *  Purpose: Main web spider/crawler class., referrerID
 *
 */
public class CLT_Spider  {
    SpiderSQLConnect dBase;
    Connection connection;

    private static final int MAX_TRAVERSE_DEPTH = 7; // n=Need to do some experimentatation with this
                                                     // to determine best value.
                                                     // Note: link depth numbering starts at (top level) and ends
                                                     // at MAX_TRAVERSE_DEPTH, ie: the lowest level. Root level
                                                     // urls have , referrerIDa "0" level.
    private static final int DAYS_TO_LIVE = 30;      // Maximum number of days a web page link lives before it
                                                     // must be updated.
    
    private static final int LINK_LIMIT = 10000;      // Limits the number of links traversed during web crawl.  Set to
                                                      // arbitrary high number to traverse a large number of links in a 
                                                      // for a production session. Or, to a low number for algorithm
                                                      // testing
    
    private static final int NO_KEYWORD_SEARCH_DEPTH = 4;  // Limits the search depth of links which have no keywords 
                                                      // found to the number specified.  Intent is that this will 
                                                      // allow the spider to trawl more deeply on sites that may have
                                                      // resources.
    
    private static final int MAX_KEYWORDS = 100;
    private static final int MAX_DISALLOWS = 250;
    private static final int SLAVE_MODE  = 0;
    private static final int MASTER_MODE = 1;
    private static final int MAX_SEARCH_KEYWORDS = 500;
    private static final int MAX_EXCLUDE_KEYWORDS = 100;
    private static final int MAX_EXCLUDE_SITES = 200;
    
    private static final int EXPIRED_MODE    = 1;
    private static final int UNEXPLORED_MODE = 2;
    private static final int RESOURCE_MODE   = 3;
    private static final int UNKNOWN_MODE    = 4;
    
    private static final int STATUS_EXPLORED_F   =  0; // no product keywords found
    private static final int STATUS_EXPLORED_J   =  5; // Junk/exclusion keyword found
    private static final int STATUS_EXPLORED_A   = 10; // CLT Product/species keywords found
    private static final int STATUS_UNEXPLORED   = 30;
    private static final int STATUS_INCOMPLETE   = 35;
    private static final int STATUS_DISALLOWED   = 40;
    private static final int STATUS_WEB_ERROR    = 50;

    
    // Create Storage for excluded keywords and sites
    String [] excludeKeywords = new String[MAX_EXCLUDE_KEYWORDS];
    String [] excludeSites = new String[MAX_EXCLUDE_SITES];
    String [] searchKeyword = new String[MAX_SEARCH_KEYWORDS];
    int    [] searchKeyID = new int[MAX_SEARCH_KEYWORDS];
    int       excludeKeywordCount, excludeSiteCount, searchKeywordCount;
    
    
    public static void main(String[] args) {
	String processOption, mode;
	int processMode = UNEXPLORED_MODE;  // default mode is to search for unexplored links/data
	int addedLinks;
	CLT_Spider spider = new CLT_Spider();
	
	System.out.println("CLT_Spider Starting......\n");

	spider.InitializeKeywordsExcludes();
        
	if (args.length >= 1) {
            processOption = args[0].toLowerCase();
	    
	    if (args.length >=2) {
		mode =  args[1].toLowerCase();
		if (mode.equals("explore")) {
		    processMode = UNEXPLORED_MODE;
		} else if (mode.equals("update")) {
		    processMode = EXPIRED_MODE;
		} else if (mode.equals("resource")) {
		    processMode = RESOURCE_MODE;
		}else {
		    processMode = UNKNOWN_MODE;
		}
	    } // 2 or more args
            
            if (processOption.equals("crawl")) {
		spider.crawlInformationLinks(processMode);
            } else if (processOption.equals("file")) {
		if (args.length >= 2)
                   spider.fileExplore(args[1]);
		else {
		    System.out.println("ERROR: html file not specified!");
		    return;
		}
            } else {
                String url = args[0];
		
                System.out.println("Scanning: "+url);
                addedLinks = spider.processURL(url, -1, MASTER_MODE, true, 1, -1);
                System.out.println("Specified URL processed. Additional Links found: " + addedLinks);
		//System.out.println(" Starting dbms_crawl mode");
                //spider.crawlInformationLinks(UNEXPLORED_MODE);
            }
            //dBase.dbClose();
            
	} else {
	    System.out.println("\nERROR: CLT_Spider processing mode un-specified!");
            System.out.println("The possible options are:\n");
            System.out.println("         java CLT_Spider file <file_of_links.html> ");
	    System.out.println("         java CLT_Spider crawl <EXPLORE|UPDATE|RESOURCE>");
	    System.out.println("	 java CLT_Spider url \n");
	    System.out.println("Use the crawl option to concentrate on building the database. Use");
	    System.out.println("the EXPLORE parameter to search for stored links that have not been");
	    System.out.println("fully explored. Or, use the UPDATE parameter to search for links");
	    System.out.println("older than 30 days that should be updated.  The database and");
	    System.out.println("spider system are designed such that multiple instances of the");
	    System.out.println("CLT-Spider can be run at the same time and operate on different");
	    System.out.println("sections of the database.\n");
	} // url to crawl was/was not specified
    } // main
   

    public void CLT_Spider() {
	dBase = new SpiderSQLConnect();
	connection = dBase.dbConnect();
	System.out.println("MySql link to CLT_Spider Database established.");
    } // empty constructor


    
    /*
     *   method: fileExplore
     * modified: 28-September-2017
     *   author: Ed Thomas
     *  purpose: Reads the data seeding information from an initialization html file. Each URL is  
     *           passed to processURL where it reads the web page, searches for keywords and  
     *           stores links for future examination by the spider. 
     *     Note: While this isn't a true search engine approach, it gives the database a set of 
     *           high-yield links that can be explored to rapidly build the database.  Once we get
     *           database initialized, we will want to pursue a more tradiation web crawl approach.
     */
    public void fileExplore(String Path) {
        Document doc=null;
	PreparedStatement st;
	int LinkSerialNumber, linkCount, addedLinks;
	String rootURL, linkURL, mainURL;
       	Elements links=null, media, imports;
	linkCount = 0;
        System.out.println("Exploring Local file: "+ Path);
        
        dBase = new SpiderSQLConnect();
	connection = dBase.dbConnect();
        
        Calendar calendar = Calendar.getInstance();
        java.sql.Date exploreDate = new java.sql.Date(calendar.getTime().getTime());
        
        try {
            File in = new File(Path);
            doc = Jsoup.parse(in, null);
        } catch (Exception e) { 
	    System.out.println("ERROR during crawl Database unexplored URL query!"); 
	    System.out.println("General Exception: " + e.getMessage());       
        }
        
        if (doc != null) {
	    try {
		links = doc.select("a[href]");
	    } catch (NullPointerException npe) {
		print("doc body read error");
	    }
	    
	    print("\nLinks: (%d)", links.size());
	    for (Element link : links) {  // loop through each link in the file.
		linkURL = standardizeURL(link.attr("abs:href"));
		rootURL = getRootURL(linkURL);
		mainURL = getMainURL(rootURL);
                
		if (!excludeURL(mainURL, excludeSites, excludeSiteCount) && rootURL.length() > 5) {
		    addedLinks = processURL(linkURL, -1, SLAVE_MODE, false, 1, -1); // level for all new links is 1
		    System.out.println(addedLinks + " additional links found.");
		    linkCount++;
		}  //else { // valid URL Test
		   // print("fileExplore:: Excluded %s", rootURL);
		   //} // !excludeURL(...

		//if (linkCount >10) break; // Limits # of reads from files for testing.
	    } // links loop

	    System.out.println("CLT Database seeded with "+linkCount+" unique links from file!");
	} // doc !null
    } // fileExplore
    
    
    
    /*
     *   method: crawlInformationLinks
     * modified: 29-September-2017
     *  purpose: Crawls the database of existing links and crawl unexplored or 
     *           out-of-date links. Current version only examines un-explored 
     *           links.
     *     NOTE: For testing purposes (and the sake of my bandwidth limits at home), 
     *           the spider is currently limited to searching through 25 links. Production 
     *           spider would run unlimitted. 
     *
     *            and right(linkURL,3) = 'pdf' 
     */
    public void crawlInformationLinks(int mode) {
	int linkID, level, count, referrer, queryCount=0, addedLinks;
	boolean update = true;
        Statement st = null;
	PreparedStatement pt;
        ResultSet rs = null;
	String url, rootURL;
	
	String UnexploredQuery = "select linkID, linkURL, level, referrerID from InformationLink " +
	                                "where status = 30 ORDER BY RAND()";
        
        String ExpiredQuery = "select linkID, linkURL, level, referrerID from InformationLink " +
	                      "where to_days(?)- to_days(discovered) > 30 ORDER BY RAND()";
        
        String ResourceQuery = "select linkID, linkURL, level, referrerID from InformationLink " +
	                                "where status = 30  and " + 
                                            "(right(linkURL,3) = 'pdf' or right(linkURL,3) = 'doc' or " +
                                            " right(linkURL,3) = 'rtf' or right(linkURL,3) = 'xls') " +
                                        " ORDER BY RAND()";            
	
        // need to test and ensure that approach to the ExpiredQueries is correct.
        String UnexploredCountQuery = "select count(*) from InformationLink " +
	                                "where status = 30"; 
        
        String ExpiredCountQuery = "select count(*) from InformationLink " +
	                               "where to_days(?) - to_days(discovered) > 30";
        
        String ResourceCountQuery = "select count(*) from InformationLink " +
	                                "where status = 30  and " + 
                                            " (right(linkURL,3) = 'pdf' or right(linkURL,3) = 'doc' or " +
                                            "  right(linkURL,3) = 'rtf' or right(linkURL,3) = 'xls') " ; 
	
	dBase = new SpiderSQLConnect();
	connection = dBase.dbConnect();
	
	Calendar calendar = Calendar.getInstance();
	java.sql.Date discoverDate = new java.sql.Date(calendar.getTime().getTime());
	java.sql.Date todaysDate = new java.sql.Date(calendar.getTime().getTime());	
	
	try {
	    if (mode == UNEXPLORED_MODE) {
		//System.out.println("-------unexplored-mode!!!-----------");
		update = false;
            	st = connection.createStatement(); 
		rs = st.executeQuery(UnexploredCountQuery); 
            	rs.next();
            	queryCount = rs.getInt(1);
            	st.close();
		
            	if (queryCount > 0) {
		    st = connection.createStatement(); 
		    rs = st.executeQuery(UnexploredQuery); 
		} // queryCount > 0
		
	    } else if (mode == EXPIRED_MODE) {
		System.out.println("---------EXPIRED-mode!!!-----------");
		System.out.println("Searching for links older than 30 days...");
		
		update = true;
		pt = connection.prepareStatement(ExpiredCountQuery);
		pt.setDate(1, todaysDate);
		
		rs = pt.executeQuery(ExpiredCountQuery);
		rs.next();
            	queryCount = rs.getInt(1);
            	pt.close();
            	
            	if (queryCount > 0) {
		    pt = connection.prepareStatement(ExpiredQuery);
		    pt.setDate(1, todaysDate);
		    
		    rs = st.executeQuery(ExpiredQuery);
            	} // queryCount > 0
            } else if (mode == RESOURCE_MODE) {
                System.out.println("---------RESOURCE-mode!!!-----------");
		System.out.println("Searching for links directly pointing to web documents");  
                    
                update = false;
            	st = connection.createStatement(); 
		rs = st.executeQuery(ResourceCountQuery); 
            	rs.next();
            	queryCount = rs.getInt(1);
            	st.close();
		
            	if (queryCount > 0) {
		    st = connection.createStatement(); 
		    rs = st.executeQuery(ResourceQuery); 
		} // queryCount > 0  
            } else {
                 System.out.println("---------Unrecognized Crawler Mode!!!-----------");
	    } // database exploration mode
	    
            
            System.out.println(" Number of links in database to crawl: " + queryCount);
            System.out.println("   Maximum number of links this crawl: " + LINK_LIMIT);
            
	    if (queryCount > 0) {
                count = 0;
                
                while (rs.next() && count < LINK_LIMIT) { // iterate through the java resultset 
                                                          // TESTHARNESS LINK LIMIT
                    //print("URL Count = %d<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<", count);
                    linkID = rs.getInt("linkID");
                    url = rs.getString("linkURL");
		    level = rs.getInt("level");
                    referrer = rs.getInt("referrerID");

		    //System.out.println("processing: " + linkID + " " + url);
		    
                    addedLinks = processURL(url, linkID, SLAVE_MODE, update, level, referrer);
		    System.out.println(addedLinks + " Links added");
		    Thread.sleep(3000L);    // Three second pause between hits
		    
                    count++;                       
                } // rs.next()
		
		st.close();
		
            } else {
            	if (mode == UNEXPLORED_MODE) 
		    System.out.println("No unexplored links exist in database!");
                else
		    System.out.println("No expired links exist in database!");

		System.out.println("Please ensure system is working correctly. This condition");
		System.out.println("should rarely, if ever, exist.");
		System.out.println("Database update crawl halting!");
            } // query returned results?
	} catch (Exception e) { 
	    System.out.println("ERROR during crawl Database unexplored URL query!"); 
	    System.out.println("General Exception: " + e.getMessage());
	} 
	dBase.dbClose();
    } // crawlInformationLinks
    
    
    
    
    /*
     *    method: processURL
     *  modified: 6-October-2017
     *   purpose: Crawls the the URL specified in the argument and stores all  
     *            keyword and sub-url links.
     * IMPORTANT: Currently, the method only performs the explore (update = false) operation.
     * arguments: (String)     url: web path to be exlplored
     *            (int)       mode: MASTER_MODE: process opens links to the data
     *                                 databases, then later closes them.
     *                              SLAVE_MODE: process is handling URLS given to it
     *                                 from CrawlDatabase, and the mysql connection 
     *                                 is already open.
     *            (boolean) update: true  if method is being called to update expired information.
     *                              false if method is being used to store new or un-explored 
     *                                    information.
     *            (int)      level: traverse or recursion level of this link
     *            (int) referrerID: LinkID of the referring URL.  -1 if manually 
     *                  entered, or from a seed html file.
     *  returns:  (int) number of unexplored additional added from current link.
     */
    public int processURL(String url, int linkID, int mode, boolean update, 
                          int level, int referrerID) {
	int i, matchKeywordCount=0, linkCount=0;
	int [] matchingKeywordID = new int[MAX_KEYWORDS];
    	int URL_Status = STATUS_EXPLORED_A;
        int status=0, age=0;
	Document doc=null;
	String text, rootLinkURL, mainLinkURL;
	String title = " ";
        String synopsis = " ";
	String [] Verboten = new String[MAX_DISALLOWS];  // list of disallow links from robots.txt
	int disallowCount=0;
	Elements links = null, media = null, imports = null;
	FindKeywords finder = null;
        FindExclusionKeywords excluder = null;
	java.sql.Date discoverDate=null, exploreDate=null, todaysDate=null;
	PreparedStatement st;
	ResultSet rs;
	Response response = null;
        
        url = standardizeURL(url);
	String rootURL = getRootURL(url);
        String mainURL = getMainURL(rootURL);

	print("Processing: %s",url);
        print("rootURL: %s", rootURL);
        print("mainURL: %s", mainURL);
		
        if (excludeURL(mainURL, excludeSites, excludeSiteCount)){
            print("ERROR: URL is on exclude list!", mainURL);
            return 0;
        } // exclude URL

	
	disallowCount = readRobotsFile(rootURL, Verboten); // read robots.txt and see what is off limits.
	//print("Process  --> Verboten[0] = %s", Verboten[0]);
	//print("Process  --> disallowCount = %d", disallowCount);
	
	Calendar calendar = Calendar.getInstance();
        todaysDate = new java.sql.Date(calendar.getTime().getTime());
	
	// see if rootURL probe is allowed.  If so, add it to queue.
	if (!isForbiddenLink(rootURL, Verboten, disallowCount) &&
	    !isDuplicateLink(rootURL)) {
                storeInformationLink(rootURL, " ", " ", " ",
                                     discoverDate, null,
                                     0, STATUS_UNEXPLORED, 0, -1, referrerID);
		
	} // rootURL okay to probe?
	
	if (isForbiddenLink(rootURL, Verboten, disallowCount)) {
	    System.out.println("root url probe disallowed in robots.txt");
	}
	
	if (isForbiddenLink(url, Verboten, disallowCount)) {
	    System.out.println("url probe disallowed in robots.txt");
	    return 0;
	} // permitted to probe URL?
	
        if (mode == MASTER_MODE) {
            dBase = new SpiderSQLConnect();
            connection = dBase.dbConnect();
        }
        
	/*
	 * Intercept links to web documents, download and index them to the link
	 * in the database.
	 */
	if (isResource(url)) {
            if (!resourceInterceptor(url, linkID, level, referrerID)){
                System.out.println("Error encountered in resourceInterceptor!");
                System.out.println("Processing continuing...");
            }
            
	    return 0;  // Resource file is a terminate, so no further links were added.
	} // trap pdf's and other content files of interest  
        
	/*
         * Trap duplicate links,  Need to write update functions for the CLT_KB crawler!!!
	 */
	if (isDuplicateLink(url)) {
	    
            if (linkID == -1) { // trap if linkID is -1, in case it is a duplicate link found during the
		// seeding phase, when the linkID will not be known by the calling function
                String statusQuery = "Select linkID, status, discovered, (to_Days(?) - to_Days(discovered))" +
		    "  from InformationLink where linkURL = ?";
                try {
                    st = connection.prepareStatement(statusQuery);
                    st.setDate    (1, todaysDate);
                    st.setString  (2, url);
		    
                    rs = st.executeQuery();
                    rs.next();
                    linkID       = rs.getInt(1);
                    status       = rs.getInt(2);
                    discoverDate = rs.getDate(3);
                    age          = rs.getInt(4);
                    st.close();
                } catch (Exception e) { 
			System.out.println("ERROR during DuplicateLink URL query Method 1:"); 
			System.out.println("General Exception: " + e.getMessage());
		}
            } else {            // linkID is known so we can get the status with a simpler query.
                String statusQuery = "Select status, discovered, (to_Days(?)- to_days(discovered))" +
		    "  from InformationLink where linkID = ?";
                
                try {
                    st = connection.prepareStatement(statusQuery);
                    st.setDate (1, todaysDate);
                    st.setInt  (2, linkID);
                
                    rs = st.executeQuery();
                    rs.next();
                    status       = rs.getInt(1);
                    discoverDate = rs.getDate(2);
                    age          = rs.getInt(3);
                    st.close();
                } catch (Exception e) { 
			System.out.println("ERROR during DuplicateLink URL query Method 1:"); 
			System.out.println("General Exception: " + e.getMessage());
		}
             } // linkID known?
            
            System.out.println("LinkID: "+linkID+" Status: "+status+" Age: "+age);
            
            /*
             * Use the status and age of the link to determine if it should be 
             * processed again.  If the status shows that it has not been processed,
             * or it has older than the  maximum DAYS_TO_LIVE, then the URL needs to be
             * processed
	     */
            
            if (!(status == STATUS_UNEXPLORED || status == STATUS_INCOMPLETE ||
                  status == STATUS_WEB_ERROR  || age >= DAYS_TO_LIVE)) {
		System.out.println("Link not updated because of status and age.");
		return 0;
            }  // link status/age test.	   
	} else {
            // if not a duplicate, set discovery date to todays date.
            discoverDate = todaysDate;
        }// isDuplicateLink
	
        
	try {
	    doc = Jsoup
		.connect(url)
		.userAgent("Mozilla/5.0 (compatible; CLTbot/0.3; +http://bbe.umn.edu/bot.html)")
		.timeout(5000)  // 5 second timeout
		.get();
	} catch (IOException e) {
	    System.out.println("URL DOC IOException: " + e.getMessage());
	    storeInformationLink(url, " ", " ", " ",discoverDate, todaysDate,0, 
				 STATUS_WEB_ERROR, level, linkID, referrerID);
	}
	
        
	if (doc != null) {
	    try {
		text = doc.body().text();
		links = doc.select("a[href]");
		media = doc.select("[src]");
		imports = doc.select("link[href]");

		// Get the title and synopsis from the web document.
                String titleText = doc.title();
                Element bodyElement = doc.body();
                String bodyText = doc.body().text();
	       
		title = titleText.substring(0, Math.min(80, titleText.length()));
                synopsis = bodyText.substring(0, Math.min(1024, bodyText.length()));
		
		excluder = new FindExclusionKeywords(doc.body().text(),excludeKeywords,
                                                     excludeKeywordCount);

		finder = new FindKeywords(doc.body().text(),searchKeyword, 
		  		          searchKeyID, searchKeywordCount);
		matchKeywordCount = finder.find();

		if (matchKeywordCount == 0)
		     URL_Status = STATUS_EXPLORED_F;
		    
		print ("Keyword Count: %d",  matchKeywordCount);
	     
	    } catch (NullPointerException npe) {
		print("doc body read error");
		text="empty";
		matchKeywordCount = 0;
		URL_Status = STATUS_WEB_ERROR;
	    } catch (Exception e) {
		print("GENERIC READ ERROR: %s",e.getMessage());
	    }
	    
	    if ((matchKeywordCount >= 0 || // has matching keywords, so store this site!
		 level <= NO_KEYWORD_SEARCH_DEPTH)) { //  && matchKeywordCount != -1) { // store this site and explore it
                
		linkID = storeInformationLink(url, title, synopsis, " ",
					      discoverDate, todaysDate, 
					      matchKeywordCount, STATUS_EXPLORED_A, level, linkID, referrerID);

		if (finder != null) {
		    matchingKeywordID = finder.getMatching();
		    
		    storeKeywords(matchingKeywordID, matchKeywordCount, linkID);
		    
		    print("\nLinks: (%d)", links.size());
		    linkCount = 0;
                } else {
		    System.out.println("Error: Finder not initialized in ProcessURL!");
                    System.out.println("Cascade error from Doc Body Read: Processing of this URL ending");
                    storeInformationLink(url, " ", " ", " ",discoverDate, todaysDate,0, 
                                STATUS_WEB_ERROR, level, linkID, referrerID);
                    return 0;
		}

		
                /*
                 * Loop through the links found on this web site and store them for 
                 * future reference.
                 */
                referrerID = linkID;
		for (Element link : links) {
		    String linkURL = standardizeURL(link.attr("abs:href"));
		    
		    if (isForbiddenLink(linkURL, Verboten, disallowCount)) {
			print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
			print ("%s excluded in robots.txt.", linkURL);
                    } else if (isDuplicateLink(linkURL)) {
                        /* If an existing link is found, during the page probe, do nothing. 
                         * However, if a recursive call to processURL is made here, then
                         * the behavior of the Spider would be to recursively process an entire
                         * URL, before moving on to the next.  There are benefits and negatives
                         * to this method of web crawling.
                         */
                        //System.out.println("Duplicate Link.");
                    } else if (isResource(linkURL)) {
                        if (!resourceInterceptor(linkURL, -1, level+1, referrerID)) {
                            System.out.println("Error handling resource at: " + linkURL);
                        }
		    } else {
			print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
			//print ("linkURL = %s", linkURL);
			
			/*
			 * Current link is not off-limits, so store it and examine it later.
			 */   
			rootLinkURL = getRootURL(linkURL);
			mainLinkURL = getMainURL(rootLinkURL);
                        
			if (mainLinkURL.equals(mainURL)) {
			    /*
			     * Current link belongs to the site currently being examined.
			     */
			    //System.out.println("Storing A: " + linkURL);
                            storeInformationLink(linkURL, " ", " ", " ", todaysDate, null,
                                                 0, STATUS_UNEXPLORED, level+1, -1, referrerID);
			    linkCount++;
			} else {
			    // Current link is external to the site being examined, 
			    
			    if (!excludeURL(mainLinkURL, excludeSites, excludeSiteCount)
				&& rootLinkURL.length() > 5) {
				//System.out.println("Storing B: " + linkURL);
                                storeInformationLink(linkURL, " ", " ", " ", todaysDate, null,
						     0, STATUS_UNEXPLORED, 1, -1, referrerID);
				linkCount++;
			    }
			} // if internal/external link
		    } // Not a forbidden link
		} // links loop
	    } else {
		/*
		 * At this point the link has been explored and does not have any keywords
		 * Two options, store the link as a bad one.  Or delete the link.  At this
		 * point we just save the link.
		 */
		storeInformationLink(url, " ", " ", " ", discoverDate, todaysDate,
				     0, STATUS_EXPLORED_F, level, linkID, referrerID);
	    } // matchKeywordCount > 0
	} else  { 
            /*
             * doc is null, which means there was an error reading the website.
             * store the error condition in the database.
             */
            storeInformationLink(url, " ", " ", " ",discoverDate, todaysDate, 0, 
				 STATUS_WEB_ERROR, level, -1, referrerID);
        }// doc != null
        
	if (mode == MASTER_MODE) {
            dBase.dbClose();
        }
	return linkCount;
    } // process method
      

    /*
     *   method: storeKeywords
     * modified: 26-July-2016
     *  purpose: Stores the keyword ID#'s for supplied URL SerialNumber.
     *  returns: nothing
     */
    private void storeKeywords(int [] keyID, int KeywordCount, int linkID) {
	int i;
        String URL_KeywordStore = " insert into KeywordLinkage (linkID, keywordID) values (?, ?)";
	
	for (i=0; i<KeywordCount; i++) {
	    try {
		//System.out.println("Storing "+linkID + "---->" + keyID[i]);
		
		PreparedStatement preparedStmt = connection.prepareStatement(URL_KeywordStore);
                preparedStmt.setInt(1, linkID);
                preparedStmt.setInt(2, keyID[i]);
		preparedStmt.execute();
		
	    } catch (Exception e) { 
		System.out.println("StoreKeywords: Error during URL keyword storage"); 
		System.out.println("General Exception: " + e.getMessage());
	    }
	} // i
    } // storeKeywords



    /*
     *     method: storeInformationLink
     *   modified: 6-October-2017
     *    purpose: Stores the information for a data or information link.  If the 
     *             URL already exists, then the method updates the existing data.
     * 
     *    returns: linkID (INT) identification of data link.
     *             0 if it was passed an invalid link type.
     *             -1 if error encountered
     *
     *       Note: Code edits on 28-7-17, assume that all duplicate link status 
     *             testing and arbitration is performed before this method is called.
     *             Thus, the job of this method is to simply store the information
     *             that it is passed.
     */
    private int storeInformationLink(String URL, String title, String synopsis, String resourcePath,
				     java.sql.Date discoverDate, java.sql.Date exploreDate, 
				     int keywordCount, int status, int level, int linkID, int referrerID) 
    {
        int existsCount = 0; //, storedStatus;
	PreparedStatement st;
	ResultSet rs;
	String uppercaseURL;
        
        String URL_Store = "INSERT into InformationLink (linkID, "+
	                   "linkURL, linkTitle, linkSynopsis, downloadPath, discovered, " +
	                   "lastExplored, keywordCount, status, level, referrerID) " +
	                   "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	String URL_Update = "Update InformationLink set " +
	                    "linkTitle=?, linkSynopsis=?, " +
	                    "downloadPath=?, lastExplored=?, " +
	                    "keywordCount=?, status=? " +
	                    "where linkID=?";
	
	// Check search depth for current url traverse.
	if (level >  MAX_TRAVERSE_DEPTH) {
	    System.out.println("Maximum search depth reached for URL! Search Halted.");
	    return 0;
	}
	
	uppercaseURL = URL.toUpperCase();
	
	// check for in-appropriate link types
	// such as mailto:

	if (uppercaseURL.indexOf("MAILTO:",0) > -1) return 0;
	
	// also email address are sometimes included as an href
	if (uppercaseURL.indexOf("@",0) > -1) return 0;
	
        if (isMultimedia(URL)) return 0;
	
	//System.out.println("------------------------------------------");
        //System.out.println("linkID: "+linkID);
        //System.out.println("URL: "+URL);
        //System.out.println("------------------------------------------");
	
	if (isDuplicateLink(URL)) { // link is already in the CLT_KB, get it's linkID
	    if (linkID == -1) {
                System.out.println("storeInformationLink: Error: linkID not set for existing CLT_KB url!");
                return -1;
            }
	    
	    try {  
		System.out.println("Updating Existing Link "+linkID+" with KeywordCount: "+keywordCount);
		    
		st = connection.prepareStatement(URL_Update);
		st.setString(1, title);
		st.setString(2, synopsis);
		st.setString(3, resourcePath);
		st.setDate  (4, exploreDate);
		st.setInt   (5, keywordCount);
		st.setInt   (6, status);
		st.setInt   (7, linkID);
		st.execute();
		st.close();
		
	    } catch (Exception e) {
		System.out.println("ERROR during InformationLink data update!"); 
		System.out.println("General Exception: " + e.getMessage());
                return -1;
	    } // try/catch block
	} else {    // Link is new, and is NOT in the CLT_KB
	    if (linkID == -1)
		linkID = getNewLinkID();  // Get a linkID if not pre-fetched for a downloaded resource

	    try {
		// create the mysql insert preparedstatement
		st = connection.prepareStatement(URL_Store);
		st.setInt    (1, linkID);
		st.setString (2, URL);
		st.setString (3, title);
		st.setString (4, synopsis);
		st.setString (5, resourcePath);
		st.setDate   (6, discoverDate);
		st.setDate   (7, exploreDate);
		st.setInt    (8, keywordCount);
		st.setInt    (9, status);
		st.setInt    (10, level);
                st.setInt    (11, referrerID);
		st.execute();
		st.close();
	    } catch (Exception e) { 
		System.out.println("ERROR during InformationLink data insert!"); 
		System.out.println("General Exception: " + e.getMessage());
                return -1;
	    } // try/catch
	} // linkExists?
   
        return linkID;
    } // storeInformationLInk
    
    
    
    /*
     *   method: getNewLinkID
     * modified: 7-July-2016
     *  purpose: returns the next available ID number for a new data link 
     *           and updates the TrackLinkID table.
     */
    private int getNewLinkID() {
	int LinkID = 0, UpdateLinkID;
        
	String query = "SELECT linkID FROM TrackLinkID";  
	String update = "update TrackLinkID set linkID = ? where linkID = ?";
 
	try {
	    Statement st = this.connection.createStatement(); // create the java statement
	    ResultSet rsa = st.executeQuery(query); // execute the query, and get a java resultset
	    rsa.next();
	    LinkID = rsa.getInt(1);
	    st.close();

	    // Now update the serial number
	    PreparedStatement prepStmt = this.connection.prepareStatement(update);
	    UpdateLinkID = LinkID + 1;
	    prepStmt.setInt(1, UpdateLinkID);
	    prepStmt.setInt(2, LinkID);
	    prepStmt.executeUpdate();
	    prepStmt.close();
	} catch (Exception e) { 
	    System.out.println("ERROR during URL SerialNumber query and update!"); 
	    System.out.println("General Exception: " + e.getMessage());
	} 
	
	return LinkID;
    } // getNewLinkID

    

    private void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }


    private String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }

    
    /*
     *  method: readRobotsFile
     * modified: 17-October-2017
     *  returns: (int) number of dis-allowed items in the off-limits (Verboten) list
     *
     * Note: A typical robots.txt file looks much like the following
     * User-agent: *
     * Disallow: /search
     * Disallow: /sdch
     * Disallow: /groups
     * Disallow: /images
     * Disallow: /catalogs
     *
     */
    private int readRobotsFile(String rootURL, String [] Verboten) {
        String roboFile = rootURL + "/robots.txt";
	String line=null, Agent="*", disallow=null;
	int index, agentIndex, space=0, disallowIndex, disallowCount;
        boolean done;

	disallowCount=0;
	
	//System.out.println("Checking robots.txt:");
	
	try{
            //print("roboFile = <<%s>>", roboFile);
	    URL robotURL  = new URL(roboFile);
            URLConnection yc = robotURL.openConnection();
	    yc.setConnectTimeout(5000); // 5 second timeout
            BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
                        
	    line = null;
            done = false;
	    while((line = in.readLine()) != null && !done) {
                
                //print("<<%s>>", line);
                
		agentIndex = line.indexOf("User-agent:", 0);
		if (agentIndex >= 0) {
		    space = line.indexOf(" ", agentIndex+10);
		    if (space < 0) // space not found
			Agent = line.substring(agentIndex+10, line.length());
		    else           // space found
			Agent = line.substring(space+1, line.length());
		} // agentIndex

		agentIndex = line.indexOf("user-agent:", 0);
		if (agentIndex >= 0) {
		    space = line.indexOf(" ", agentIndex+10);
		    if (space < 0) // space not found
			Agent = line.substring(agentIndex+10, line.length());
		    else           // space found
			Agent = line.substring(space+1, line.length());
		} // agentIndex
		
	       	disallowIndex = line.indexOf("Disallow:", 0);
		if (disallowIndex >= 0) {
		    space = line.indexOf(" ", disallowIndex+10);
		    if (space < 0) // space not found
			disallow = line.substring(disallowIndex+10, line.length());
		    else           // space found
			disallow = line.substring(space+1, line.length());
		} else { // check for lowercase disallow...
		    disallowIndex = line.indexOf("disallow:", 0);
		    if (disallowIndex >= 0) {
			space = line.indexOf(" ", disallowIndex+10);
			if (space < 0) // space not found
			    disallow = line.substring(disallowIndex+10, line.length());
			else           // space found
			    disallow = line.substring(space+1, line.length());
		    } // disallowIndex
		}
						
		if (disallowIndex >=0 && (Agent.equals("*") || Agent.equals("WoodBot"))) {
		    if (disallowCount < MAX_DISALLOWS) {
			Verboten[disallowCount] = rootURL+disallow;
			disallowCount++;
		    } // if
		}
		
                if (disallowCount >= 250){
                    done = true;
                    print("%d maximum disallow count reached, robots.txt file read halted.", MAX_DISALLOWS);
                }
                
		//System.out.println(line);
	       		
	    } // while
	    in.close();

	    //print("Total disallows: %d", disallowCount);
	    
	} catch (MalformedURLException e) {
	    System.out.println("Malformed URL Exception: "+rootURL);
	    System.out.println("Error: " + e.getMessage());
	} catch (IOException e) {
	    System.out.println("IO Exception: "+rootURL); // Indicates a missing robots.txt file
	    System.out.println("Error: " + e.getMessage());
	    return 0; // no items in the dis-allow list
	} catch (Exception e) {
	    System.out.println("Error: Possible syntax error in robots.txt file at line:");
	    print("{%s} caused a general exception error:", line);
	    System.out.println(e.getMessage());
	}

	return disallowCount;
    } // readRobotsFile
    
    
    
    /*
     *   method: standardizeURL
     * modified: 3-August-2017
     *  purpose: Some links have a trailing slash, some don't.  The ending slash is meaningless,
     *           but the system will create two entries in the database for a link if both 
     *           versions are encountered. This method standardizes URLs by removing the 
     *           trailing slash when it is found. 
     * modified: 27/7/16 to also remove trailing # and #abcde from URLs.
     *           3/18/17 to also remove training ? and & codes.
     */
    public String standardizeURL(String URL) {
	String cleanURL;
	int StrLength, Index;

	StrLength = URL.length();
	cleanURL = URL.substring(0,StrLength);
	
	if (StrLength < 5)  // URL too short to be legitimate URL.
	    return URL;
	
	if (URL.charAt(StrLength-1) == '/') 
	    cleanURL = URL.substring(0,StrLength-1);
	
	//Index = cleanURL.indexOf("#", 0);  
	//if (Index > 0)
	//    cleanURL = cleanURL.substring(0, Index);
	
        //Index = cleanURL.indexOf("?", 0);  
	//if (Index > 0)
	//    cleanURL = cleanURL.substring(0, Index);
        
        Index = cleanURL.indexOf("&", 0);  
	if (Index > 0)
	    cleanURL = cleanURL.substring(0, Index);
        
	return cleanURL;
    }// standardizeLink
    
    
    
    /* 
     *   method: getRootURL
     * modified: 26-July-2016
     */
    private String getRootURL(String url) {
	String rootURL;
	int Index;
	
	//System.out.println("getRootURL Processing: " + url);
	
	Index = url.indexOf("/", url.indexOf("://") + 3); // This finds the end of the root URL
        if (Index <0)
	    rootURL = url;
	else
	    rootURL = url.substring(0, Index);

	Index = rootURL.indexOf("?", 0);  // This finds the end of the root URL
	if (Index > 0)
	    rootURL = rootURL.substring(0, Index);
	
	//System.out.println("RootURL = "+ rootURL);
	
	return rootURL;
    } // getRootURL
 

    /* 
     *   method: getMainURL
     * modified: 8-January-2016
     *  purpose:  returns the mainURL portion of a URL. for example a rootURL of
     *            "http://www.google.com" this method will return "google.com"
     */
    private String getMainURL(String rootURL) {
	String mainURL="";
	int Index, StrLen, lastDot;
        boolean done;
        char c;
        
        StrLen = rootURL.length();
        lastDot = rootURL.lastIndexOf(".");
        
        done = false;
        Index = lastDot-1;
        while(!done && Index > 0){
            c = rootURL.charAt(Index);
            
            if (c=='.') {
		//System.out.println("DOT!");
		//print("<<%s>>", rootURL.substring(Index+1, lastDot));
		if (!rootURL.substring(Index+1, lastDot).equals("co")){
		    mainURL = rootURL.substring(Index+1,StrLen); 
		    done = true;
		} // co foreign country identification test
            } // dot found
            
            if (c=='/'){
                mainURL = rootURL.substring(Index+1,StrLen);
                //System.out.println("<<"+mainURL+">>");
                done = true;
            }
            
            Index--;
        } // done

        return mainURL;
    } // getMainURL

    
    /*
     *    method: excludeURL
     *  modified: 15-January-2016
     *   purpose: Allows spider to quickly determine if a URL has a non-
     *            probe robots.txt, or is a URL that happens to show for
     *            forest products keyword searches, but has nothing to do 
     *            with any aspect of forestry or forest products. 
     *   returns: true if rootURL is named as a URL not to store or probe.
     *            false otherwise
     */
    private boolean excludeURL(String mainURL, String [] excludedSites, int excludeCount) {
	int i;

	//System.out.println("testing exclusion: <<"+mainURL+">>");
	
	for (i=0; i<excludeCount; i++) {
	    if (mainURL.toLowerCase().equals(excludedSites[i])) {
                //System.out.println("EXCLUDED");
		return true;
	    }
	} // i
	
	return false;
    } // excludeURL


    /*
     *   method: forbiddenLink
     * modified: 5-January-2016
     *  purpose: Returns true if the link is on the robots.txt exclude list for all or our
     */
    private boolean isForbiddenLink(String URL, String [] forbiddenLinks, int linkCount) {
	int i = 0;

	while (i < linkCount) {
	    if (forbiddenLinks[i].equals("*") ||
		forbiddenLinks[i].equals(URL)) return true;
	    i++;
	} // i

	return false;
    }// isForbiddenLink

    
    
    /*
     *   method: InitializeKeywordsExcludes
     * modified: 18-January-2016
     *  purpose: Loads the lists of exclusion keywords and site names from the
     *           mySQL database.
     */
    private void InitializeKeywordsExcludes() {
	SpiderSQLConnect dBaseInit;
	Connection connectionInit;
        Statement st;
        ResultSet rs;
        
	try {	    
	    String keywordQuery = "Select Keyword FROM ExcludeKeywords";
	    String urlQuery = "Select MainURL from ExcludedSites";
            String searchKeywordQuery = "SELECT KeyWord, KeywordID FROM Keywords";
	    
	    dBaseInit = new SpiderSQLConnect();
	    connectionInit = dBaseInit.dbConnect();
            st = connectionInit.createStatement();
            rs = st.executeQuery(keywordQuery);
	    
            excludeKeywordCount = 0;
            while (rs.next() && 
                   excludeKeywordCount < MAX_EXCLUDE_KEYWORDS) { // iterate through the java resultset
                excludeKeywords[excludeKeywordCount] = rs.getString("Keyword");
                excludeKeywordCount++;
                
                if (excludeKeywordCount == MAX_EXCLUDE_KEYWORDS)
                    print("Maximum exclusion keywords loaded.  Truncating list...");
            } // rs.next()
            st.close();
                    
            st = connectionInit.createStatement();
	    rs = st.executeQuery(urlQuery);
            
            excludeSiteCount = 0;
             while (rs.next() && 
                    excludeSiteCount < MAX_EXCLUDE_SITES) { // iterate through the java resultset
                excludeSites[excludeSiteCount] = rs.getString("MainURL");
                excludeSiteCount++;
                
                if (excludeSiteCount == MAX_EXCLUDE_SITES)
                    print("Maximum exclusion sites loaded.  Truncating list...");
            } // rs.next()
            st.close();
            
            
            st = connectionInit.createStatement(); // create the java statement
	    rs = st.executeQuery(searchKeywordQuery); // execute the query, and get a java resultset
	    
            searchKeywordCount = 0;
            while (rs.next() && 
                    searchKeywordCount < MAX_SEARCH_KEYWORDS) { // iterate through the java resultset
		searchKeyword[searchKeywordCount] = rs.getString("KeyWord");
		searchKeyID[searchKeywordCount] = rs.getInt("KeywordID");
                searchKeywordCount++;
                
                if (searchKeywordCount == MAX_SEARCH_KEYWORDS)
                    print("Maximum exclusion sites loaded.  Truncating list...");
            } // rs.next()
                       
            print("%d product and species keywords loaded!", searchKeywordCount );
            print("%d keyword exclusions loaded!", excludeKeywordCount );
            print("%d site exclusions loaded!", excludeSiteCount);
        } catch (Exception e) {
	     System.out.println("loadExcluded: Error loading exlusion keywords and site names");
             System.out.println(e.getMessage());
	} // try/catch block
    
    } // loadExcluded


    /*
     *    method: arbitrateStatus
     *  modified: 26-July-2016
     *   purpose: Examines the stored status value and the default input status value and
     *            decides what the actual status value should be.  Simple routine to avoid
     *            overwriting valid data.
     * arguments: (int) newStatus: potential new link status value
     *            (int) storedStatus: The current status value stored in the CLT_KB database
     *                                for the link in question.
     *   returns: (int) arbitrated status-value.  
     */ 
    private int arbitrateStatus(int newStatus, int storedStatus) {
	int status = 0;
	
	switch(storedStatus) {
	    
	case STATUS_UNEXPLORED: // if previously un-explored default to newStatus
	    status = newStatus;
	    break;
	    
	case STATUS_EXPLORED_F:
	case STATUS_EXPLORED_A:
	case STATUS_INCOMPLETE:
	case STATUS_DISALLOWED:
	case STATUS_WEB_ERROR:
	    if (newStatus == STATUS_UNEXPLORED) 
		status = storedStatus;
	    else
		status = newStatus;
	} // status switch
	return status;
    } // arbitrateStatus


    /*
     *    method: isDuplicateLink
     *  modified: 26-July-2017
     *   purpose: Determines if links already exist in the CLT_KB.
     * arguments: (String) url to be queried to test for duplication in CLT_KB
     *   returns: true if link is already in the database
     *            false
     */ 
    private boolean isDuplicateLink(String URL) {
	PreparedStatement st;
	ResultSet rs;
	boolean isDuplicate = false;
	String query = "SELECT COUNT(*) FROM InformationLink where linkURL = ?";
	
	try {	
	    st = connection.prepareStatement(query);
	    st.setString (1, URL);
	    rs = st.executeQuery(); 
	    rs.next();
	    if (rs.getInt(1) >= 1)
		isDuplicate = true;
	    st.close();
	} catch (Exception e) { 
	    System.out.println("isDuplicateLink: Error, Search failed for existing InformationLink"); 
	    System.out.println("General Exception: " + e.getMessage());
	}

	return isDuplicate;
    } // isDuplicateLink
	

    /*
     *  method: isResource
     * purpose: returns true if url ends in a resource file type extension, 
     *          false otherwise.
     */
    public boolean isResource(String url) {
        
        if (url.toLowerCase().endsWith(".pdf")  || url.toLowerCase().endsWith(".txt")  ||
	    url.toLowerCase().endsWith(".doc")  || url.toLowerCase().endsWith(".docx") ||
	    url.toLowerCase().endsWith(".rtf")  || url.toLowerCase().endsWith(".csv")  ||
	    url.toLowerCase().endsWith(".pptx") || url.toLowerCase().endsWith(".ppt")  ||
	    url.toLowerCase().endsWith(".xml")  || url.toLowerCase().endsWith(".xlsx") ||
	    url.toLowerCase().endsWith(".xls")  || url.toLowerCase().endsWith(".zip")) 
            return true;
            
        return false;
    } // is Resource
    
    
    /*
     *  method: isMultimedia
     * purpose: returns true if url ends in a multimedia or other invalid resource
     *                       file type extension, 
     *          false otherwise.
     *    note: In no way does this pretend to be a complete list.  We are only trying 
     *          to catch the most common multimedia file types.
     */
    public boolean isMultimedia(String url) {
        if (url.toLowerCase().endsWith(".jpg")  || 
            url.toLowerCase().endsWith(".jpeg") ||
	    url.toLowerCase().endsWith(".gif")  || 
            url.toLowerCase().endsWith(".tif")  ||
	    url.toLowerCase().endsWith(".png")  || 
            url.toLowerCase().endsWith(".ogg")  || 
            url.toLowerCase().endsWith(".m4a")  || 
            url.toLowerCase().endsWith(".mpg")  ||
	    url.toLowerCase().endsWith(".mp4")  ||
            url.toLowerCase().endsWith(".mp3")  ||
            url.toLowerCase().endsWith(".mpeg") ||
            url.toLowerCase().endsWith(".avi")  ||
            url.toLowerCase().endsWith(".bat")  ||  // check for executable files
            url.toLowerCase().endsWith(".exe")  ||
            url.toLowerCase().endsWith(".com")) 
            return true;
        
       return false;
       } // isMultimedia
    
    
    /*
     *   method: ResourceDownloader
     * modified: 19-July-2017
     *  purpose:
     *  returns: true upon success
     *           false upon failure/error
     */
    public static boolean ResourceDownloader(String strURL, String path) 
    {
    InputStream input = null;
    OutputStream output = null;
    HttpURLConnection connection = null;
    
    try {
        URL url = new URL(strURL);
        
        HttpURLConnection.setFollowRedirects(true); 	
        
        connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000); //set timeout to 5 seconds
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        // expect HTTP 200 OK, so we don't mistakenly save error report
        // instead of the file
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            System.out.println("Server returned HTTP " + connection.getResponseCode()
			       + " " + connection.getResponseMessage());
	    return false;
        }

        // this will be useful to display download percentage
        // might be -1: server did not report the length
        int fileLength = connection.getContentLength();

        // download the file
        input = connection.getInputStream();
        output = new FileOutputStream(path);

        byte data[] = new byte[4096];
        int count;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
        }
    } catch (java.net.SocketTimeoutException e) {
        System.out.println("Connection timeout error: "+e.toString());
        return false;
    } catch (Exception e) {
	System.out.println("Download error: "+e.toString());
        return false;
    } finally {
        try {
            if (output != null)
                output.close();
            if (input != null)
                input.close();
        } catch (IOException ignored) {
        }

        if (connection != null)
            connection.disconnect();
    }
    return true;
    } // ResourceDownloader

    
    /*
     *   method: resourceInterceptor
     * modified: 6-October-2017
     *  purpose: Handles web resources when found and determines if they are related
     *           to the manufacturer or specifications of CLT.
     *  returns: false if error condition encoutered
     *           true if interceptor completed without error
     */    
    public boolean resourceInterceptor(String url, int linkID, int level, int referrerID) {
        java.sql.Date discoverDate=null, exploreDate=null, todaysDate=null;
        PreparedStatement st;
        ResultSet rs;
        FindKeywords finder; 
        int i, matchKeywordCount;
        int [] matchingKeywordID;
        
        
        if (linkID == -1) {            
            if (isDuplicateLink(url)) {
                /*
                 * If the link is already in the CLT_KB, then we need to handle it differently.
                 */
                System.out.println("Resource Link exists in CLT_KB, Update Function required");
		
                return false;
            } else {
                /*
                 * Link and resource are not duplicates, so download the resource and 
                 * store the link in the database.
                 */
		linkID = getNewLinkID(); // prefetch a linkID for the download
            } // Duplicate Link Trap
        } // linkID == -1
	
	
	String fileTitle = url.substring( url.lastIndexOf("/") + 1 );
	String filePath = "Download/" + "<<"+linkID+">>"+fileTitle;
	
	System.out.println("Downloading: "+ url);
	System.out.println("         to: "+ filePath);
	
	if (ResourceDownloader(url, filePath)) {
	    
	    TikaParse tikaparser = new TikaParse();
	    String resourceText = tikaparser.parseStream(filePath);
	    
	    finder = new FindKeywords(resourceText, searchKeyword, 
				      searchKeyID, searchKeywordCount);
	    matchKeywordCount = finder.find();
	    
	    if (matchKeywordCount > 0) {
		System.out.println("Resource keyword matches: "+matchKeywordCount);
                
		storeInformationLink(url, fileTitle, " ", filePath,
				     todaysDate, todaysDate,
				     matchKeywordCount, STATUS_EXPLORED_A, level, linkID, referrerID);
		
		matchingKeywordID = finder.getMatching();
		storeKeywords(matchingKeywordID, matchKeywordCount, linkID);
	    } else {
		// no keywords found!
		// Delete the downloaded resource and store this information
		System.out.println("Downloaded resource: " + filePath + " had no keyword matches!");
                
		try{
		    File purgeFile = new File(filePath);
		    
		    if (purgeFile.delete())
			System.out.println(purgeFile.getName() + " is deleted!");
		    else
			System.out.println("Delete failed for: " + filePath);
		    
		} catch(Exception e){
		    e.printStackTrace();
		} // try/catch block
                
		storeInformationLink(url, fileTitle, " ", " ", todaysDate, todaysDate,
				     0, STATUS_EXPLORED_F, level, linkID, referrerID);
	    } // matchKeywordCount
	} else {
	    System.out.println("Download Failed!: Updating Link Status to Error Condition");
	    
	    try {
		String URL_Update = "Update InformationLink set status=? where linkID=?"; 
		
		st = connection.prepareStatement(URL_Update);
		st.setInt   (1, STATUS_WEB_ERROR);
		st.setInt   (2, linkID);
		st.execute();
		st.close();
	    } catch (Exception e) { 
		System.out.println("ERROR during URL Download Status change!"); 
		System.out.println("General Exception: " + e.getMessage());
	    }
	    
	}
	
	return true; // No Error condition
    } // ResourceInterceptor
    
} // WoodSpider Class
