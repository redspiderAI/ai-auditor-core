use crate::DocumentSection;
use serde::Serialize;

/// In-memory document tree used for downstream processing and for mapping back to XML offsets.
#[derive(Debug, Clone, Default, Serialize)]
pub struct DocumentTree {
    pub root: SectionNode,
    pub metadata: DocumentMetadata,
}

#[derive(Debug, Clone, Serialize)]
pub struct DocumentMetadata {
    pub total_elements: usize,
    pub heading_count: usize,
    pub table_count: usize,
}

#[derive(Debug, Clone, Serialize)]
pub struct SectionNode {
    pub id: i32,
    pub title: String,
    pub level: u8,
    pub xml_path: String,
    pub children: Vec<SectionItem>,
}

#[derive(Debug, Clone, Serialize)]
pub enum SectionItem {
    #[serde(rename = "subsection")]
    Subsection(SectionNode),
    #[serde(rename = "content")]
    Content(DocumentSection),
}