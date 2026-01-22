use crate::{DocumentSection, ElementType};
use anyhow::Result;
use std::collections::HashMap;
use std::path::Path;

pub trait PdfParser {
    /// Parse a PDF document and return a DocumentTree
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::core::layout::DocumentTree>;
}

/// PdfParser implementation using lopdf
pub struct LopdfParser;

impl PdfParser for LopdfParser {
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::core::layout::DocumentTree> {
        use lopdf::Document;

        // Load the PDF document
        let doc = Document::load(path.as_ref())?;

        let mut sections = Vec::new();
        let mut section_id = 1;

        // Get all page numbers
        let pages = doc.get_pages();

        // Iterate through pages
        for (page_num, _) in pages.iter() {
            // Extract text from the page
            if let Ok(content) = doc.extract_text(&[*page_num]) {
                // Split content into paragraphs/sections
                let paragraphs: Vec<&str> = content.lines().collect();

                for paragraph in paragraphs {
                    if !paragraph.trim().is_empty() {
                        let formatting = self.extract_formatting_for_pdf(paragraph);

                        let section = DocumentSection {
                            id: section_id,
                            element_type: ElementType::Paragraph, // For now, treat everything as paragraph
                            raw_text: paragraph.to_string(),
                            formatting,
                            xml_path: format!("page_{}.txt", page_num), // Using page number as reference
                        };

                        sections.push(section);
                        section_id += 1;
                    }
                }
            }
        }

        // Create a new DocumentTree with all required fields
        Ok(crate::core::layout::DocumentTree {
            sections,
            positions: std::collections::HashMap::new(),
            metadata: crate::core::layout::DocumentMetadata {
                total_pages: pages.len() as u32,
                file_path: path.as_ref().to_string_lossy().to_string(),
                file_size: std::fs::metadata(path.as_ref())?.len(),
                creation_date: None,
                modification_date: None,
            }
        })
    }
}

impl LopdfParser {
    /// Extract formatting information from PDF text
    fn extract_formatting_for_pdf(&self, _text: &str) -> HashMap<String, String> {
        let mut formatting = HashMap::new();
        formatting.insert("element-type".to_string(), "pdf-text".to_string());
        formatting
    }
}