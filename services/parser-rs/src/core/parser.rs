use crate::{DocumentSection, ElementType, core::pdf_parser::PdfParser};
use anyhow::Result;
use roxmltree::Document;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::collections::HashMap;
use zip::ZipArchive;

pub trait Parser {
    /// Parse a document (path to .docx or stream) and return a DocumentTree
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::core::layout::DocumentTree>;
}

/// Universal parser that handles multiple document formats
pub struct UniversalParser {
    pub docx_parser: crate::core::parser::DocxParser,
    pub pdf_parser: crate::core::pdf_parser::LopdfParser,
}

impl UniversalParser {
    pub fn new() -> Self {
        Self {
            docx_parser: crate::core::parser::DocxParser,
            pdf_parser: crate::core::pdf_parser::LopdfParser,
        }
    }

    pub fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::core::layout::DocumentTree> {
        let path_str = path.as_ref().to_string_lossy().to_lowercase();

        if path_str.ends_with(".docx") {
            self.docx_parser.parse(path)
        } else if path_str.ends_with(".pdf") {
            // Use the PDF parser
            self.pdf_parser.parse(path)
        } else {
            anyhow::bail!("Unsupported file format: {}", path_str);
        }
    }
}

/// DocxParser: implementation that extracts paragraph texts from `word/document.xml` using roxmltree.
pub struct DocxParser;

impl DocxParser {
    /// Extract formatting properties from a paragraph node
    fn extract_formatting(&self, p_node: roxmltree::Node, _doc: &Document) -> HashMap<String, String> {
        let mut formatting = HashMap::new();

        // Look for paragraph properties (pPr)
        if let Some(p_pr) = p_node.children()
            .find(|child| child.is_element() && child.tag_name().name() == "pPr") {

            // Extract indentation
            if let Some(ind) = p_pr.children()
                .find(|child| child.is_element() && child.tag_name().name() == "ind") {
                if let Some(left) = ind.attribute("w:left") {
                    formatting.insert("indent-left".to_string(), left.to_string());
                }
            }

            // Extract spacing
            if let Some(spacing) = p_pr.children()
                .find(|child| child.is_element() && child.tag_name().name() == "spacing") {
                if let Some(line_spacing) = spacing.attribute("w:line") {
                    formatting.insert("line-spacing".to_string(), line_spacing.to_string());
                }
            }

            // Extract outline level (for headings)
            if let Some(outline_lvl) = p_pr.children()
                .find(|child| child.is_element() && child.tag_name().name() == "outlineLvl") {
                if let Some(lvl) = outline_lvl.attribute("w:val") {
                    formatting.insert("outline-level".to_string(), lvl.to_string());
                }
            }
        }

        // Look for run properties (rPr) in the first run of the paragraph
        if let Some(run) = p_node.descendants()
            .find(|n| n.is_element() && n.tag_name().name() == "r") {
            if let Some(r_pr) = run.children()
                .find(|child| child.is_element() && child.tag_name().name() == "rPr") {

                // Extract font size
                if let Some(sz) = r_pr.children()
                    .find(|child| child.is_element() && child.tag_name().name() == "sz") {
                    if let Some(val) = sz.attribute("w:val") {
                        formatting.insert("font-size".to_string(), val.to_string());
                    }
                }

                // Extract font family
                if let Some(r_fonts) = r_pr.children()
                    .find(|child| child.is_element() && child.tag_name().name() == "rFonts") {
                    if let Some(font_ascii) = r_fonts.attribute("w:ascii") {
                        formatting.insert("font-family".to_string(), font_ascii.to_string());
                    }
                }
            }
        }

        formatting
    }

    /// Determine element type based on paragraph properties
    fn determine_element_type(&self, p_node: roxmltree::Node, _doc: &Document) -> ElementType {
        // Check for heading styles based on outline level
        if let Some(p_pr) = p_node.children()
            .find(|child| child.is_element() && child.tag_name().name() == "pPr") {

            if let Some(outline_lvl) = p_pr.children()
                .find(|child| child.is_element() && child.tag_name().name() == "outlineLvl") {
                if let Some(lvl_str) = outline_lvl.attribute("w:val") {
                    if let Ok(level) = lvl_str.parse::<u8>() {
                        return ElementType::Heading(level);
                    }
                }
            }
        }

        // Check for table elements
        if p_node.children().any(|child| child.is_element() && child.tag_name().name() == "tbl") {
            return ElementType::Table;
        }

        // Check for equation elements
        if p_node.children().any(|child| child.is_element() && child.tag_name().name() == "oMath") {
            return ElementType::Equation;
        }

        // Default to paragraph
        ElementType::Paragraph
    }

    /// Extract text from a paragraph node
    fn extract_text_from_paragraph(&self, p_node: roxmltree::Node) -> String {
        let mut text_parts = Vec::new();

        // Look for text elements inside runs
        for t in p_node.descendants().filter(|n| n.is_element() && n.tag_name().name() == "t") {
            if let Some(txt) = t.text() {
                // Trim whitespace and add to parts
                let trimmed = txt.trim();
                if !trimmed.is_empty() {
                    text_parts.push(trimmed);
                }
            }
        }

        text_parts.join(" ")
    }
}

impl Parser for DocxParser {
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::core::layout::DocumentTree> {
        let file = File::open(path.as_ref())?;
        let mut archive = ZipArchive::new(file)?;

        // Read document.xml
        let mut doc_xml = String::new();
        {
            let mut file = archive.by_name("word/document.xml")?;
            file.read_to_string(&mut doc_xml)?;
        }
        let doc = Document::parse(&doc_xml)?;

        // Read styles.xml to get style definitions
        let mut _styles_xml = String::new();
        // Since we can't access the archive twice, we'll handle styles differently
        // For now, we'll skip reading styles.xml to avoid the double borrow
        let _styles_doc: Option<Document> = None;

        let mut sections = Vec::new();
        let mut section_id = 1;

        // Process paragraphs
        for p_node in doc.descendants().filter(|n| n.is_element() && n.tag_name().name() == "p") {
            let text = self.extract_text_from_paragraph(p_node);
            let formatting = self.extract_formatting(p_node, &doc);
            let element_type = self.determine_element_type(p_node, &doc);

            let section = DocumentSection {
                id: section_id,
                element_type,
                raw_text: text,
                formatting,
                xml_path: format!("word/document.xml#/w:document/w:body/w:p[{}]", section_id),
            };

            sections.push(section);
            section_id += 1;
        }

        // Process tables separately
        for tbl_node in doc.descendants().filter(|n| n.is_element() && n.tag_name().name() == "tbl") {
            let text = self.extract_table_text(tbl_node);
            let formatting = self.extract_formatting_for_table(tbl_node, &doc);

            let section = DocumentSection {
                id: section_id,
                element_type: ElementType::Table,
                raw_text: text,
                formatting,
                xml_path: format!("word/document.xml#/w:document/w:body/w:tbl[{}]", section_id),
            };

            sections.push(section);
            section_id += 1;
        }

        // Create a new DocumentTree with all required fields
        Ok(crate::core::layout::DocumentTree {
            sections,
            positions: std::collections::HashMap::new(),
            metadata: crate::core::layout::DocumentMetadata {
                total_pages: 0, // Will be filled by the caller or from document properties
                file_path: path.as_ref().to_string_lossy().to_string(),
                file_size: std::fs::metadata(path.as_ref())?.len(),
                creation_date: None,
                modification_date: None,
            }
        })
    }
}

impl DocxParser {
    /// Extract text from a table node
    fn extract_table_text(&self, tbl_node: roxmltree::Node) -> String {
        let mut text_parts = Vec::new();

        // Iterate through table rows and cells
        for tr_node in tbl_node.children().filter(|n| n.is_element() && n.tag_name().name() == "tr") {
            for tc_node in tr_node.children().filter(|n| n.is_element() && n.tag_name().name() == "tc") {
                for p_node in tc_node.children().filter(|n| n.is_element() && n.tag_name().name() == "p") {
                    let cell_text = self.extract_text_from_paragraph(p_node);
                    if !cell_text.is_empty() {
                        text_parts.push(cell_text);
                    }
                }
            }
        }

        text_parts.join("\n")
    }

    /// Extract formatting for table elements
    fn extract_formatting_for_table(&self, _tbl_node: roxmltree::Node, _doc: &Document) -> HashMap<String, String> {
        let mut formatting = HashMap::new();
        formatting.insert("element-type".to_string(), "table".to_string());
        formatting
    }
}
