use crate::{core::parser::UniversalParser, core::writer::DocxWriter, grpc::Issue, core::writer::Writer};
use anyhow::Result;
use std::path::Path;
use std::fs;
use log::{info, warn, error, debug};

pub struct DocumentProcessor {
    parser: UniversalParser,
    writer: DocxWriter,
}

impl DocumentProcessor {
    pub fn new() -> Self {
        Self {
            parser: UniversalParser::new(),
            writer: DocxWriter,
        }
    }

    /// Process all documents in the input directory and write results to output directory
    pub fn process_documents<P: AsRef<Path>>(&self, input_dir: P, output_dir: P) -> Result<()> {
        let input_path = input_dir.as_ref();
        let output_path = output_dir.as_ref();

        info!("Starting to process documents from: {}", input_path.display());
        
        // Create output directory if it doesn't exist
        fs::create_dir_all(output_path)?;
        info!("Output directory prepared: {}", output_path.display());

        // Get all student directories in the input directory
        let entries = fs::read_dir(input_path)
            .map_err(|e| anyhow::anyhow!("Failed to read input directory: {}", e))?;
            
        for entry in entries {
            let entry = entry.map_err(|e| anyhow::anyhow!("Failed to read directory entry: {}", e))?;
            let file_path = entry.path();

            if file_path.is_dir() {
                info!("Processing directory: {:?}", file_path.file_name());
                
                // Process each directory (student submission)
                match self.process_student_directory(&file_path, &output_path) {
                    Ok(_) => info!("Successfully processed directory: {:?}", file_path.file_name()),
                    Err(e) => {
                        error!("Failed to process directory {:?}: {}", file_path.file_name(), e);
                        // Continue with other directories
                        continue;
                    }
                }
            } else {
                debug!("Skipping non-directory entry: {:?}", file_path.file_name());
            }
        }

        info!("Completed processing all documents");
        Ok(())
    }

    /// Process a single student directory containing their documents
    fn process_student_directory<P1: AsRef<Path>, P2: AsRef<Path>>(&self, student_dir: P1, output_dir: P2) -> Result<()> {
        let student_path = student_dir.as_ref();
        let output_path = output_dir.as_ref();

        info!("Processing student directory: {}", student_path.display());

        // Create a directory for this student's output
        let student_output_dir = output_path.join(student_path.file_name().unwrap_or_default());
        fs::create_dir_all(&student_output_dir)?;
        debug!("Created output directory: {}", student_output_dir.display());

        // Process all files in the student directory
        let entries = fs::read_dir(student_path)
            .map_err(|e| anyhow::anyhow!("Failed to read student directory: {}", e))?;
            
        for entry in entries {
            let entry = entry.map_err(|e| anyhow::anyhow!("Failed to read file entry: {}", e))?;
            let file_path = entry.path();

            if file_path.is_file() {
                let file_ext = file_path.extension()
                    .and_then(|ext| ext.to_str())
                    .unwrap_or("")
                    .to_lowercase();

                match file_ext.as_str() {
                    "docx" | "pdf" => {
                        info!("Processing file: {}", file_path.display());
                        
                        // Parse the document
                        match self.parser.parse(&file_path) {
                            Ok(document_tree) => {
                                // Generate issues for the parsed document
                                let issues = self.generate_sample_issues(&document_tree);
                                
                                // Write annotated document to output if there are issues
                                if !issues.is_empty() {
                                    info!("Found {} issues in file: {}", issues.len(), file_path.display());
                                    
                                    let output_file_path = student_output_dir.join(format!(
                                        "{}_annotated.{}",
                                        file_path.file_stem().unwrap_or_default().to_string_lossy(),
                                        file_ext
                                    ));
                                    
                                    // Apply annotations to the document
                                    match self.apply_annotations(&file_path, &output_file_path, &issues, &file_ext) {
                                        Ok(_) => {
                                            info!("Successfully created annotated file: {}", output_file_path.display());
                                            
                                            // Also save issues to a separate file
                                            if let Err(e) = self.save_issues_to_file(&issues, &student_output_dir, &file_path) {
                                                warn!("Failed to save issues file for {:?}: {}", file_path.file_name(), e);
                                            }
                                        },
                                        Err(e) => {
                                            error!("Failed to apply annotations to {:?}: {}", file_path.file_name(), e);
                                            // Copy original file as fallback
                                            if let Err(copy_err) = fs::copy(&file_path, &output_file_path) {
                                                error!("Failed to copy original file as fallback: {}", copy_err);
                                            }
                                        }
                                    }
                                } else {
                                    info!("No issues found in file: {}", file_path.display());
                                    
                                    // Copy the original file without annotations
                                    let output_file_path = student_output_dir.join(file_path.file_name().unwrap_or_default());
                                    if let Err(e) = fs::copy(&file_path, &output_file_path) {
                                        error!("Failed to copy file {:?}: {}", file_path.file_name(), e);
                                    }
                                }
                            },
                            Err(e) => {
                                error!("Failed to parse document {:?}: {}", file_path.file_name(), e);
                                // Copy the original file as fallback
                                let output_file_path = student_output_dir.join(file_path.file_name().unwrap_or_default());
                                if let Err(copy_err) = fs::copy(&file_path, &output_file_path) {
                                    error!("Failed to copy original file as fallback: {}", copy_err);
                                }
                            }
                        }
                    }
                    _ => {
                        debug!("Copying non-processable file: {}", file_path.display());
                        // Copy other files without processing
                        let output_file_path = student_output_dir.join(file_path.file_name().unwrap_or_default());
                        if let Err(e) = fs::copy(&file_path, &output_file_path) {
                            error!("Failed to copy file {:?}: {}", file_path.file_name(), e);
                        }
                    }
                }
            } else {
                debug!("Skipping non-file entry: {:?}", file_path.file_name());
            }
        }

        Ok(())
    }

    /// Apply annotations to a document based on the file extension
    fn apply_annotations<P: AsRef<Path>>(
        &self,
        input_path: P,
        output_path: P,
        issues: &[Issue],
        file_ext: &str,
    ) -> Result<()> {
        match file_ext {
            "docx" => {
                // Apply annotations to DOCX file
                self.writer.write_annotations(input_path.as_ref(), output_path.as_ref(), issues)
            }
            "pdf" => {
                // For PDF files, we can't currently add annotations, so just copy the original
                fs::copy(input_path.as_ref(), output_path.as_ref())
                    .map_err(|e| anyhow::anyhow!("Failed to copy PDF file: {}", e)).map(|_| ())
            }
            _ => {
                // For other file types, just copy the original
                fs::copy(input_path.as_ref(), output_path.as_ref())
                    .map_err(|e| anyhow::anyhow!("Failed to copy file: {}", e)).map(|_| ())
            }
        }
    }

    /// Generate sample issues for demonstration purposes
    /// In a real implementation, this would connect to the validation engine
    fn generate_sample_issues(&self, document_tree: &crate::core::layout::DocumentTree) -> Vec<Issue> {
        let mut issues = Vec::new();
        
        info!("Analyzing {} sections in document", document_tree.sections.len());
        
        // Example: Check for sections that might have formatting issues
        for (index, section) in document_tree.sections.iter().enumerate() {
            debug!("Analyzing section {}: {} chars, type: {:?}", 
                   section.id, section.raw_text.len(), section.element_type);
            
            // Example validation rules
            // More intelligent length check: count words instead of just characters
            let word_count = section.raw_text.split_whitespace().count();
            if word_count > 100 {  // More reasonable threshold: 100+ words
                issues.push(Issue {
                    id: (index + 1) as i64,
                    message: format!("Section too long ({word_count} words), consider breaking it into smaller parts", word_count=word_count).to_string(),
                    section_id: section.id,
                });
            }
            
            // Check for potential formatting issues
            if section.element_type == crate::ElementType::Paragraph && 
               section.formatting.contains_key("font-size") {
                if let Some(font_size) = section.formatting.get("font-size") {
                    if font_size != "12" && font_size != "24" { // Allow both pt sizes
                        issues.push(Issue {
                            id: (index + 2) as i64,
                            message: format!("Font size should be 12pt for body text, found: {}pt", font_size),
                            section_id: section.id,
                        });
                    }
                }
            }
            
            // Check for heading levels
            if let crate::ElementType::Heading(level) = section.element_type {
                if level > 6 {
                    issues.push(Issue {
                        id: (index + 3) as i64,
                        message: format!("Heading level {} is too deep, maximum allowed is 6", level),
                        section_id: section.id,
                    });
                }
            }
        }
        
        info!("Generated {} issues for document", issues.len());
        issues
    }

    /// Save issues to a separate file
    fn save_issues_to_file<P: AsRef<Path>>(
        &self,
        issues: &[Issue],
        output_dir: P,
        original_file_path: P,
    ) -> Result<()> {
        use std::fs::OpenOptions;
        use std::io::Write;

        let issues_file_path = output_dir.as_ref().join(format!(
            "{}_issues.txt",
            original_file_path.as_ref().file_stem().unwrap_or_default().to_string_lossy()
        ));

        let mut file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(&issues_file_path)
            .map_err(|e| anyhow::anyhow!("Failed to create issues file: {}", e))?;

        writeln!(file, "Issues found in: {}", original_file_path.as_ref().display())
            .map_err(|e| anyhow::anyhow!("Failed to write to issues file: {}", e))?;
        writeln!(file, "Number of issues: {}", issues.len())
            .map_err(|e| anyhow::anyhow!("Failed to write to issues file: {}", e))?;
        writeln!(file)
            .map_err(|e| anyhow::anyhow!("Failed to write to issues file: {}", e))?;

        for issue in issues {
            writeln!(file, "Issue {}: Section {} - {}", issue.id, issue.section_id, issue.message)
                .map_err(|e| anyhow::anyhow!("Failed to write issue to file: {}", e))?;
        }

        info!("Saved {} issues to file: {}", issues.len(), issues_file_path.display());
        Ok(())
    }
}

// Example usage function
pub fn run_document_processing() -> Result<()> {
    // Initialize logger if not already initialized
    if env_logger::try_init().is_err() {
        // Logger already initialized
    }
    
    info!("Initializing document processor...");
    let processor = DocumentProcessor::new();
    
    // Define input and output directories - use absolute paths
    let current_dir = std::env::current_dir().unwrap();
    let repo_root = current_dir.parent().unwrap().parent().unwrap(); // Go up two levels to repo root
    let input_dir = repo_root.join("data").join("input");
    let output_dir = repo_root.join("data").join("output");
    
    info!("Starting document processing from '{}' to '{}'", input_dir.display(), output_dir.display());
    
    // Process all documents
    processor.process_documents(input_dir, output_dir)?;
    
    info!("Document processing completed!");
    
    Ok(())
}