use crate::{DocumentSection, ElementType};
use anyhow::Result;
use roxmltree::Document;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::collections::HashMap;
use zip::ZipArchive;

pub trait Parser {
    /// Parse a document (path to .docx or stream) and return a DocumentTree
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::layout::DocumentTree>;
}

/// DocxParser: implementation that extracts paragraph texts from `word/document.xml` using roxmltree.
pub struct DocxParser;

impl Parser for DocxParser {
    fn parse<P: AsRef<Path>>(&self, path: P) -> Result<crate::layout::DocumentTree> {
        let file = File::open(path.as_ref())?;
        let mut archive = ZipArchive::new(file)?;

        let mut doc_xml = String::new();
        let mut file = archive.by_name("word/document.xml")?;
        file.read_to_string(&mut doc_xml)?;

        let doc = Document::parse(&doc_xml)?;

        let mut sections = Vec::new();
        for (i, node) in doc.descendants().filter(|n| n.is_element() && n.tag_name().name() == "p").enumerate() {
            let mut text_parts = Vec::new();
            for t in node.descendants().filter(|n| n.is_element() && n.tag_name().name() == "t") {
                if let Some(txt) = t.text() {
                    text_parts.push(txt);
                }
            }
            let text = text_parts.join("");
            let section = DocumentSection {
                id: (i as i32) + 1,
                element_type: ElementType::Paragraph,
                raw_text: text,
                formatting: HashMap::new(),
                xml_path: format!("word/document.xml#/w:document/w:body/w:p[{}]", i + 1),
            };
            sections.push(section);
        }

        Ok(crate::layout::DocumentTree { sections })
    }
}
