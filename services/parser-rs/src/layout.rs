use crate::DocumentSection;

/// In-memory document tree used for downstream processing and for mapping back to XML offsets.
#[derive(Debug, Clone, Default)]
pub struct DocumentTree {
    pub sections: Vec<DocumentSection>,
}

impl DocumentTree {
    pub fn new() -> Self {
        Self { sections: Vec::new() }
    }
}
