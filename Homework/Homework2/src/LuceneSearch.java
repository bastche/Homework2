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
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class LuceneSearch {
    public static void main(String[] args) throws Exception {
        String indexDirectory = "D:\\\\Ingénierie des données\\\\Index"; // Replace with your index path

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirectory)));
        IndexSearcher searcher = new IndexSearcher(reader);

        Scanner scanner = new Scanner(System.in);
        String field = null;
        
        while (!"author".equalsIgnoreCase(field) && 
                !"content".equalsIgnoreCase(field) && 
                !"abstract".equalsIgnoreCase(field) && 
                !"title".equalsIgnoreCase(field)) {
        	System.out.print("Enter the type of your query between 'author', 'content', 'abstract', 'title'  : ");
        	field = scanner.nextLine().toLowerCase();
         }
        
        
        
        System.out.print("Enter your query : ");
        String queryStr = scanner.nextLine().toString();
        
        // Define a default analyzer (e.g., StandardAnalyzer)
        Analyzer defaultAnalyzer = new StandardAnalyzer();
        

        // Create specific analyzers for certain fields
        Analyzer titleAnalyzer = CustomAnalyzer.builder()
        		.withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
        		.addTokenFilter(PatternReplaceFilterFactory.class,
        		"pattern", "[^a-zA-Z-]","replacement", "") // Remove non-alphabetic characters except hyphens
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

        QueryParser parser = new QueryParser(field, multiFieldAnalyzer);
        Query query = parser.parse(queryStr);

        // Execute the query
        org.apache.lucene.search.TopDocs hits = searcher.search(query, 10);
        StoredFields storedFields = searcher.storedFields();
        
        
        if (hits.scoreDocs.length >1) {
	        System.out.println("Results : ");
	        for (int i = 0; i < hits.scoreDocs.length; i++) {		
	            Document doc = storedFields.document(hits.scoreDocs[i].doc);
	            System.out.println("Score: " + hits.scoreDocs[i].score);
	            System.out.println("Title: " + doc.get("title"));
	            if (!"title".equalsIgnoreCase(field)) {
	            System.out.println(field.substring(0, 1).toUpperCase() + field.substring(1)+": "+ doc.get(field));
	            }
	            System.out.println("------------------------");
	        }
        } else {
        	System.out.println("No matches were found for a "+field+" with the following terms : "+ queryStr);
        }
        reader.close();
        scanner.close();
    }
}