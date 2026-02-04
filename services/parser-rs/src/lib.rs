pub mod parser;
pub mod docx_parser;
pub mod layout;
pub mod layout_modeler;
pub mod document;
pub mod protobuf_converter;
pub mod protocol_formatter;
pub mod oss_client;
pub mod parsed_data_converter;
pub mod writer;
pub mod grpc;
pub mod grpc_server;

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
