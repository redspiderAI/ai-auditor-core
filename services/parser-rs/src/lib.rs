pub mod parser;
pub mod layout;
pub mod writer;
pub mod grpc;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DocumentSection {
    pub id: i32,
    pub element_type: ElementType,
    pub raw_text: String,
    pub formatting: std::collections::HashMap<String, String>,
    pub xml_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ElementType {
    Heading(u8),
    Paragraph,
    Table,
    Equation,
}
