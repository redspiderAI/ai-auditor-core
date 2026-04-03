package com.auditor.engine.service.validators;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Document Type Validator
 * 
 * Function: Check if required fields are complete based on document type
 * Supported document types:
 *   - thesis: Academic thesis
 *   - journal: Journal article
 *   - conference: Conference paper
 *   - book: Monograph
 */
@Component
public class DocumentTypeValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentTypeValidator.class);
    
    // Define required fields for different document types
    private static final Map<String, String[]> REQUIRED_FIELDS = new HashMap<>();
    
    static {
        // Thesis: requires degree, school, advisor, etc.
        REQUIRED_FIELDS.put("thesis", new String[]{
            "title", "author", "school", "advisor", "year", "degree", "abstract"
        });
        
        // Journal article: requires journal name, volume, issue, pages, etc.
        REQUIRED_FIELDS.put("journal", new String[]{
            "title", "author", "journal", "volume", "issue", "pages", "year", "doi"
        });
        
        // Conference paper: requires conference name, location, date, etc.
        REQUIRED_FIELDS.put("conference", new String[]{
            "title", "author", "conference", "location", "date", "pages", "year"
        });
        
        // Monograph: requires edition, print count, etc.
        REQUIRED_FIELDS.put("book", new String[]{
            "title", "author", "publisher", "year", "edition", "isbn"
        });
    }
    
    /**
     * Validate document type and required fields
     * 
     * @param data Parsed document data
     * @return List of found issues
     */
    public List<Issue> validateDocumentType(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || !data.hasMetadata()) {
            logger.warn("Input data is null or missing metadata");
            return issues;
        }
        
        try {
            DocumentMetadata metadata = data.getMetadata();
            
            // Infer document type from title field (since proto has no documentType field)
            String title = metadata.getTitle();
            String docType = inferDocumentType(title);
            
            logger.debug("Inferred document type: {}", docType);
            
            // Check if document type is supported
            if (!REQUIRED_FIELDS.containsKey(docType)) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_UNKNOWN")
                        .setMessage("Unknown document type: " + docType)
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("Please specify a valid document type: thesis, journal, conference, book")
                        .build();
                issues.add(issue);
                logger.warn("Unknown document type: {}", docType);
                return issues;
            }
            
            // Get required fields for this document type
            String[] requiredFields = REQUIRED_FIELDS.get(docType);
            
            // Check basic required fields
            if (title.isEmpty()) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_MISSING_TITLE")
                        .setMessage("Document is missing a title")
                        .setSeverity(Severity.HIGH)
                        .setSuggestion("Please provide a document title")
                        .build();
                issues.add(issue);
            }
            
            if (metadata.getPageCount() <= 0) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_MISSING_PAGES")
                        .setMessage("Invalid document page count")
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("Please ensure the document has a valid page count")
                        .build();
                issues.add(issue);
            }
            
            // Perform additional checks based on document type
            checkDocumentTypeSpecificRules(docType, metadata, issues);
            
            logger.info("Document type validation completed, found {} issues", issues.size());
            
        } catch (Exception e) {
            logger.error("Document type validation exception", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_TYPE_VALIDATION")
                    .setMessage("Document type validation exception: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
    
    /**
     * Infer document type from title
     */
    private String inferDocumentType(String title) {
        if (title.isEmpty()) {
            return "unknown";
        }
        
        String lowerTitle = title.toLowerCase();
        
        if (lowerTitle.contains("学位论文") || lowerTitle.contains("thesis") || 
            lowerTitle.contains("dissertation")) {
            return "thesis";
        } else if (lowerTitle.contains("期刊") || lowerTitle.contains("journal") ||
                   lowerTitle.contains("article")) {
            return "journal";
        } else if (lowerTitle.contains("会议") || lowerTitle.contains("conference") ||
                   lowerTitle.contains("proceedings")) {
            return "conference";
        } else if (lowerTitle.contains("专著") || lowerTitle.contains("book")) {
            return "book";
        }
        
        return "thesis"; // Default to thesis
    }
    
    /**
     * Perform additional checks based on document type
     */
    private void checkDocumentTypeSpecificRules(String docType, DocumentMetadata metadata, List<Issue> issues) {
        switch (docType) {
            case "thesis":
                checkThesisSpecificRules(metadata, issues);
                break;
            case "journal":
                checkJournalSpecificRules(metadata, issues);
                break;
            case "conference":
                checkConferenceSpecificRules(metadata, issues);
                break;
            case "book":
                checkBookSpecificRules(metadata, issues);
                break;
        }
    }
    
    /**
     * Thesis specific rules
     */
    private void checkThesisSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // Thesis should have sufficient pages
        if (metadata.getPageCount() < 20) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_THESIS_PAGES")
                    .setMessage("Thesis page count is too low, should be at least 20 pages")
                    .setSeverity(Severity.MEDIUM)
                    .setSuggestion("Add more content to the thesis")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * Journal article specific rules
     */
    private void checkJournalSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // Journal articles are usually shorter
        if (metadata.getPageCount() > 50) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_JOURNAL_PAGES_LONG")
                    .setMessage("Journal article has many pages, please confirm if it is a journal article")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("Check if the document type is correct")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * Conference paper specific rules
     */
    private void checkConferenceSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // Conference papers are usually 4-8 pages
        if (metadata.getPageCount() < 4) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_CONF_PAGES_SHORT")
                    .setMessage("Conference paper page count is too low, usually 4-8 pages")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("Check if the document is complete")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * Monograph specific rules
     */
    private void checkBookSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // Monographs are usually thick
        if (metadata.getPageCount() < 50) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_BOOK_PAGES_SHORT")
                    .setMessage("Monograph page count is too low, usually over 50 pages")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("Check if the document is complete")
                    .build();
            issues.add(issue);
        }
    }
}