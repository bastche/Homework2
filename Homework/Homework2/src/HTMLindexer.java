import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.pattern.PatternReplaceFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import java.time.Duration;
import java.time.Instant;


public class HTMLindexer {

    private static String INDEX_DIR = "D:\\Ingénierie des données\\Index";
    private static String HTML_DIR = "D:\\Ingénierie des données\\urls_htmls_tables\\urls_htmls_tables\\all_htmls";
    private static Path path = Paths.get(INDEX_DIR);
    private static int counter = 0;
    
    public static void main(String[] args) {
        try {
            // Create the index
            Directory dir = FSDirectory.open(path);
            
            // Define a default analyzer (e.g., StandardAnalyzer)
            Analyzer defaultAnalyzer = new StandardAnalyzer();
            

            // Create specific analyzers for certain fields
            Analyzer titleAnalyzer = CustomAnalyzer.builder()
            		.withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
            		.addTokenFilter(PatternReplaceFilterFactory.class,
            		"pattern", "[^a-zA-Z0-9-]","replacement", "") // Remove non-alphabetic characters except hyphens
            		.addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
            		.addTokenFilter(ASCIIFoldingFilterFactory.class) // Handle accents by transforming them
            		.addTokenFilter(StopFilterFactory.class,
            		"ignoreCase", "true") // Remove stopwords in English
            		.build();
            Analyzer contentAnalyzer = CustomAnalyzer.builder()
                    .addCharFilter(PatternReplaceCharFilterFactory.class, "pattern", "<[^>]+>", "replacement", " ") // Step 1: Remove HTML tags (replace tags with spaces)
                    .withTokenizer(StandardTokenizerFactory.class) // Step 2: Standard tokenizer, suitable for long and complex texts
                    .addTokenFilter(LowerCaseFilterFactory.class) // Step 3: Convert all content to lowercase
                    .addTokenFilter(ASCIIFoldingFilterFactory.class) // Step 4: Remove accents
                    .addTokenFilter(StopFilterFactory.class, "ignoreCase", "true") // Step 5: Remove stopwords using the predefined CharArraySet
                    //.addTokenFilter(SynonymGraphFilterFactory.class, "synonyms", "synonyms.txt", "expand", "true") // Step 6: Synonym filter (external file containing synonyms)
                    .addTokenFilter(SnowballPorterFilterFactory.class, "language", "English") // Step 7: Stemming filter to obtain word roots
                    .build();
            Analyzer authorAnalyzer = CustomAnalyzer.builder()
                    // Tokenization by space
                    .withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
                    .addTokenFilter(PatternReplaceFilterFactory.class, // Remove non-alphabetic characters except hyphens
                            "pattern", "[^a-zA-Z-]", "replacement", "")
                    .addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
                    .addTokenFilter(StopFilterFactory.class,      // Remove stopwords
                            "ignoreCase", "true") // Use the default list of stopwords (in English)
                    .build();
            //Analyzer abstractAnalyzer = new StandardAnalyzer();  // For "abstract", to be done

            // Associate each field with its specific analyzer
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("title", titleAnalyzer);
            analyzerPerField.put("author", authorAnalyzer);
            analyzerPerField.put("content", contentAnalyzer);
            analyzerPerField.put("abstract", contentAnalyzer);
            
            // Create a PerFieldAnalyzerWrapper to handle analyzers per field
            Analyzer multiFieldAnalyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField);
            
            IndexWriterConfig config = new IndexWriterConfig(multiFieldAnalyzer);
            IndexWriter writer = new IndexWriter(dir, config);
            
            // Record the start time
            Instant startTime = Instant.now();
            
            // Index HTML files
            File[] files = new File(HTML_DIR).listFiles((dir1, name) -> name.endsWith(".html"));
            if (files != null) {
                for (File file : files) {
                
                    indexHTMLFile(writer, file);
                    counter +=1 ;
                }
            }
            writer.commit();
            writer.close();
            
            Instant endTime = Instant.now();
            // Calculate the time taken and the indexing rate
            long timeElapsed = Duration.between(startTime, endTime).toMillis(); // time in milliseconds
            double timeElapsedSeconds = timeElapsed / 1000.0;
            
            System.out.println("Indexing completed of "+counter+" in "+ timeElapsedSeconds+"s.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void indexHTMLFile(IndexWriter writer, File file) throws IOException {
        try {
            // Read and parse the HTML file
            org.jsoup.nodes.Document htmlDoc = Jsoup.parse(file, "UTF-8");

            // Extract the title
            String title = htmlDoc.title();
            // Extract the body content (without tags)
            String content = htmlDoc.select("div.ltx_page_content").text();
            // Extract the abstract content (without tags)
            String resume = htmlDoc.select("div.ltx_abstract").text();
            // Extract the authors
            Elements metaTags = htmlDoc.select("span.ltx_personname");
            String author = "";
            for (int i = 0; i < metaTags.size(); i++) {
                org.jsoup.nodes.Element span = metaTags.get(i);
                author += span.text().replaceAll("[^a-zA-Z\\p{P}\\s]", "");
                // Add a comma if it's not the last element
                if (i < metaTags.size() - 1) {
                    author += ", ";
                }
            }
            
            // Create a Lucene document
            Document doc = new Document();

            // Add fields to the document with specific analyzers
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));
            doc.add(new TextField("author", author, Field.Store.YES));
            doc.add(new TextField("abstract", resume, Field.Store.YES));
            writer.addDocument(doc);
            System.out.println("Indexed file : " + file.getName());

        } catch (Exception e) {
            System.err.println("Error indexing file " + file.getName() + ": " + e.getMessage());
        }
    }

}
