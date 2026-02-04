use crate::DocumentSection;
use serde::Serialize;

/// Simplified in-memory document tree: just a flat list of sections.
#[derive(Debug, Clone, Default, Serialize)]
pub struct DocumentTree {
    pub sections: Vec<DocumentSection>,
}