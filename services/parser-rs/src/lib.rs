pub mod core {
    pub mod parser;
    pub mod pdf_parser;
    pub mod layout;
    pub mod writer;
}

pub mod utils {
    pub mod document_processor;
}

pub mod grpc;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DocumentSection {
    pub id: i32,
    pub element_type: ElementType,
    pub raw_text: String,
    pub formatting: std::collections::HashMap<String, String>,
    pub xml_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ElementType {
    Heading(u8),
    Paragraph,
    Table,
    Equation,
}
