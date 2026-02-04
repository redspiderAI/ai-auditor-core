<<<<<<< HEAD
pub mod parser;
pub mod layout;
pub mod grpc_server;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
=======
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
>>>>>>> main
pub struct DocumentSection {
    pub id: i32,
    pub element_type: ElementType,
    pub raw_text: String,
    pub formatting: std::collections::HashMap<String, String>,
    pub xml_path: String,
}

<<<<<<< HEAD
#[derive(Debug, Clone, Serialize, Deserialize)]
=======
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
>>>>>>> main
pub enum ElementType {
    Heading(u8),
    Paragraph,
    Table,
    Equation,
}
