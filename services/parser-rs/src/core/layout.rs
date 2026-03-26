use crate::DocumentSection;

/// Represents physical coordinates and positioning information for document elements
#[derive(Debug, Clone, Default, PartialEq)]
pub struct PositionInfo {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
    pub page_number: Option<u32>,
}

/// In-memory document tree used for downstream processing and for mapping back to XML offsets.
#[derive(Debug, Clone, Default)]
pub struct DocumentTree {
    pub sections: Vec<DocumentSection>,
    pub positions: std::collections::HashMap<i32, PositionInfo>, // Maps section ID to position info
    pub metadata: DocumentMetadata,
}

/// Additional metadata about the parsed document
#[derive(Debug, Clone, Default)]
pub struct DocumentMetadata {
    pub total_pages: u32,
    pub file_path: String,
    pub file_size: u64,
    pub creation_date: Option<String>,
    pub modification_date: Option<String>,
}

impl DocumentTree {
    pub fn new() -> Self {
        Self {
            sections: Vec::new(),
            positions: std::collections::HashMap::new(),
            metadata: DocumentMetadata::default(),
        }
    }

    /// Add a section with its position information
    pub fn add_section_with_position(&mut self, section: DocumentSection, position: PositionInfo) {
        self.sections.push(section.clone());
        self.positions.insert(section.id, position);
    }

    /// Get a section by its ID
    pub fn get_section_by_id(&self, id: i32) -> Option<&DocumentSection> {
        self.sections.iter().find(|section| section.id == id)
    }

    /// Get position information for a section by its ID
    pub fn get_position_by_id(&self, id: i32) -> Option<&PositionInfo> {
        self.positions.get(&id)
    }

    /// Update a section's content
    pub fn update_section(&mut self, id: i32, new_content: String) {
        if let Some(section) = self.sections.iter_mut().find(|s| s.id == id) {
            section.raw_text = new_content;
        }
    }
}
