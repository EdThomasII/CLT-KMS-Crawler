/*
 *    class: CrawlerErrorLogger
 *   author: Ed Thomas
 * modified: 16-November-2017
 *  purpose: This is a simple class that logs errors encountered by the web crawler and
 *           appends the error messages to the "Crawler-Error.log" file.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;



public class CrawlerErrorLogger {
    boolean append = true;
    
    File ErrorLog = null;                            // Create file and stream objects for crawler 
    BufferedWriter ErrorStream = null;               // error reporting
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    
    
    public CrawlerErrorLogger() {
        try {
            ErrorLog = new File("Crawler-Error.log");
            ErrorStream = new BufferedWriter(new FileWriter(ErrorLog, append));
            
            String stamp  = dateFormat.format(new Date());
            System.out.println(stamp + " Error logging for Crawler started");
            System.out.println(stamp + " Error Log File: 'Crawler-Error.log'");
            
            ErrorStream.write(stamp);
            ErrorStream.write("===================================================================");
            ErrorStream.newLine();
            
            ErrorStream.write(stamp);
            ErrorStream.write("  UMN CLT Spider Started");
            ErrorStream.newLine();
            ErrorStream.flush();
            
        } catch (Exception e) {
            System.out.println("ERROR: Unable to crawler error logging!"); 
	    System.out.println("General Exception: " + e.getMessage()); 
        }
    } // constructor
       
    
    
    public void haltLogger(){
        try {
	    String stamp  = dateFormat.format(new Date());
	    System.out.println(stamp + "  Crawler Instance stopped!");
	    
            ErrorStream.flush();     // push any unwritten data to the file
            ErrorStream.close();     
        } catch (Exception ignore) {
            // do Nothing
        }
    } // haltLogger
    
    
    
    /*
     *   method: WriteErrMsg
     *   author: Ed Thomas
     * modified: 16-November-2107
     *     uses: Global var ErrorStream
     *  purpose: Writes errors encounted by the crawler to the log file.  This 
     *           provides a record of errors and problems encountered by the 
     *           crawler and can be used to improve the behaviour and performance]
     *           of the crawler.
     */
    public void WriteErrMsg(Exception e, String URL, int linkID, String msg1, String msg2) {
        String stamp  = dateFormat.format(new Date());
            
        if (ErrorStream != null) {
            try {  
                if (e != null) {
                    ErrorStream.write(stamp);
		    ErrorStream.write("  ");
                    ErrorStream.write(e.getMessage());
                    ErrorStream.newLine();
                }
                
                if (linkID != 0) {                      // If 0 then linkID was unknown, 
                    ErrorStream.write(stamp);           //   or error unrelated to linkid
		    ErrorStream.write("  ");
                    ErrorStream.write("LinkID: ");     
                    ErrorStream.write(String.valueOf(linkID));
                    ErrorStream.newLine();
                }
                
                ErrorStream.write(stamp);
		ErrorStream.write("  ");
                ErrorStream.write(msg1);
                ErrorStream.newLine();
                
                ErrorStream.write(stamp);
		ErrorStream.write("  ");
                ErrorStream.write(msg2);
                ErrorStream.newLine();
                
                ErrorStream.newLine();
                ErrorStream.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } //finally {                       // always close the file
              //  if (ErrorStream != null) {
              //      try {
              //          ErrorStream.close();
              //      } catch (IOException ioe2) {
              //          // just ignore it
              //      }
              //  }
              //} // end try/catch/finally
        } else {
            System.out.println("Warning: Crawler Error Log offline!");
        }
    } //LogCrawlerError
} // CrawlerErrorLogger
