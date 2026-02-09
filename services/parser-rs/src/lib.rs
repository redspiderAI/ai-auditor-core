pub mod core {
    pub mod layout;
    pub mod layout_modeler;
    pub mod parser;
    pub mod pdf_parser;
    pub mod writer;
}

pub mod utils {
    pub mod document_processor;
}

pub mod grpc;

use serde::{ser::Serializer, Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DocumentSection {
    pub id: i32,
    #[serde(rename = "type", serialize_with = "serialize_element_type")]
    pub element_type: ElementType,
    pub raw_text: String,
    #[serde(skip_serializing_if = "std::collections::HashMap::is_empty", default)]
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

fn serialize_element_type<S>(value: &ElementType, serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    match value {
        ElementType::Heading(_) => serializer.serialize_str("heading"),
        ElementType::Paragraph => serializer.serialize_str("paragraph"),
        ElementType::Table => serializer.serialize_str("table"),
        ElementType::Equation => serializer.serialize_str("equation"),
    }
}
