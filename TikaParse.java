/*
 *    class: TikaParse
 * modified: 29-September-2017
 *  purpose:
 */

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import static java.nio.charset.StandardCharsets.UTF_8;


public class TikaParse {
    String text;
    Metadata metadata;
    TikaConfig tikaConfig;
    
    public TikaParse() {
        
        tikaConfig = TikaConfig.getDefaultConfig(); 
    } // TikaParse

    
   public String parseStream(String filename) {
    metadata = new Metadata(); 

    try {
    //text = parseUsingComponents(filename, tikaConfig, metadata); // method 1
    text = parseUsingAutoDetect(filename, tikaConfig, metadata); // method 2
    } catch (Exception e) { 
        System.out.println("Exception thrown in TikaParse:parseStream!");
        System.out.println("Error: " + e.getMessage());
    }
    //System.out.println("FileStream Parsed Successfully!");
    
    return text;
   } // parseStream
   
   
    public static String parseUsingAutoDetect(String filename, TikaConfig tikaConfig,
                                              Metadata metadata) 
    {    
        ContentHandler handler = null;
        
        //System.out.println("Handling using AutoDetectParser: [" + filename + "]");
         
        try {
            AutoDetectParser parser = new AutoDetectParser(tikaConfig);
            handler = new BodyContentHandler();
            TikaInputStream stream = TikaInputStream.get(new File(filename), metadata);
            parser.parse(stream, handler, metadata, new ParseContext());
        } catch (Exception e) {
          System.out.println("Exception: " + e.getMessage()+ "trapped in TikaParse.parseUsingAutoDetect");
        }
        
        //System.out.println("Success!");
        return handler.toString();
    } // parseUsingAutoDetect

    
    public static String parseUsingComponents(String filename, TikaConfig tikaConfig,
                                              Metadata metadata) throws Exception {
        MimeTypes mimeRegistry = tikaConfig.getMimeRepository();

        System.out.println("Examining: [" + filename + "]");

        metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
        System.out.println("The MIME type (based on filename) is: ["
                + mimeRegistry.detect(null, metadata) + "]");

        InputStream stream = TikaInputStream.get(new File(filename));
        System.out.println("The MIME type (based on MAGIC) is: ["
                + mimeRegistry.detect(stream, metadata) + "]");

        stream = TikaInputStream.get(new File(filename));
        Detector detector = tikaConfig.getDetector();
        System.out.println("The MIME type (based on the Detector interface) is: ["
                + detector.detect(stream, metadata) + "]");

        LanguageIdentifier lang = new LanguageIdentifier(new LanguageProfile(
                FileUtils.readFileToString(new File(filename), UTF_8)));

        System.out.println("The language of this content is: ["
                + lang.getLanguage() + "]");

        // Get a non-detecting parser that handles all the types it can
        Parser parser = tikaConfig.getParser();
        // Tell it what we think the content is
        MediaType type = detector.detect(stream, metadata);
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
        // Have the file parsed to get the content and metadata
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, new ParseContext());

        return handler.toString();
    }
}
