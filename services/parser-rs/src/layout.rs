use crate::DocumentSection;
use serde::Serialize;

/// In-memory document tree used for downstream processing and for mapping back to XML offsets.
#[derive(Debug, Clone, Default, Serialize)]
pub struct DocumentTree {
    pub sections: Vec<DocumentSection>,
}

impl DocumentTree {
    pub fn new() -> Self {
        Self { sections: Vec::new() }
    }
}
