// src/parser.rs
use std::collections::HashMap;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct DocumentSection {
    pub id: i32,
    #[serde(rename = "type")]
    pub element_type: ElementType,
    pub raw_text: String,
    pub formatting: HashMap<String, String>,
    pub xml_path: String,
}

#[derive(Debug, Clone, Serialize)]
pub enum ElementType {
    #[serde(rename = "heading")]
    Heading(u8),
    #[serde(rename = "paragraph")]
    Paragraph,
    #[serde(rename = "table")]
    Table,
    #[serde(rename = "equation")]
    Equation,
}

pub trait Parser {
    fn parse(&self, file_path: &str) -> anyhow::Result<Vec<DocumentSection>>;
}