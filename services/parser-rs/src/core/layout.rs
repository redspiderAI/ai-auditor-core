use crate::DocumentSection;
use std::collections::HashMap;

/// Physical coordinates for a parsed element (pt units, page-based).
#[derive(Debug, Clone, Default, PartialEq)]
pub struct PositionInfo {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
    pub page_number: Option<u32>,
}

/// Section tree node; headings become nodes, other elements become `Content` children.
#[derive(Debug, Clone)]
pub struct SectionNode {
    pub id: i32,
    pub title: String,
    pub level: u8,
    pub xml_path: String,
    pub children: Vec<SectionItem>,
}

impl Default for SectionNode {
    fn default() -> Self {
        Self {
            id: 0,
            title: "Root".to_string(),
            level: 0,
            xml_path: "document.xml#root".to_string(),
            children: Vec::new(),
        }
    }
}

/// Tree item: either a nested heading or a piece of content (paragraph/table/equation).
#[derive(Debug, Clone)]
pub enum SectionItem {
    Subsection(SectionNode),
    Content(DocumentSection),
}

/// Aggregated document metadata (counts + optional file info).
#[derive(Debug, Clone, Default)]
pub struct DocumentMetadata {
    pub total_elements: usize,
    pub heading_count: usize,
    pub table_count: usize,
    pub total_pages: Option<u32>,
    pub file_path: Option<String>,
    pub file_size: Option<u64>,
}

/// Complete in-memory representation including layout positions.
#[derive(Debug, Clone)]
pub struct DocumentTree {
    pub root: SectionNode,
    pub metadata: DocumentMetadata,
    pub positions: HashMap<i32, PositionInfo>, // Maps section ID to position info
}

impl DocumentTree {
    pub fn new(root: SectionNode, metadata: DocumentMetadata) -> Self {
        Self {
            root,
            metadata,
            positions: HashMap::new(),
        }
    }

    pub fn with_positions(mut self, positions: HashMap<i32, PositionInfo>) -> Self {
        self.positions = positions;
        self
    }

    pub fn get_position_by_id(&self, id: i32) -> Option<&PositionInfo> {
        self.positions.get(&id)
    }
}
