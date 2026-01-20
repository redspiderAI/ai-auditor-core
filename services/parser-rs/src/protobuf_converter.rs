// src/protobuf_converter.rs
use crate::layout::{DocumentTree as RustDocumentTree, SectionNode as RustSectionNode, SectionItem as RustSectionItem};
use crate::parser::DocumentSection as RustDocumentSection;
use crate::parser::ElementType as RustElementType;
use crate::document::*; // 自动生成的 Protobuf 模块

impl From<RustElementType> for ElementType {
    fn from(rust_type: RustElementType) -> Self {
        match rust_type {
            RustElementType::Paragraph => ElementType::Paragraph,
            RustElementType::Heading(_) => ElementType::Heading,
            RustElementType::Table => ElementType::Table,
            RustElementType::Equation => ElementType::Equation,
        }
    }
}

impl From<RustDocumentSection> for DocumentSection {
    fn from(rust_section: RustDocumentSection) -> Self {
        let mut formatting = std::collections::HashMap::new();
        for (k, v) in rust_section.formatting {
            formatting.insert(k, v);
        }

        DocumentSection {
            id: rust_section.id,
            r#type: ElementType::from(rust_section.element_type) as i32,
            raw_text: rust_section.raw_text,
            formatting,
            xml_path: rust_section.xml_path,
        }
    }
}

impl From<RustDocumentTree> for DocumentTree {
    fn from(rust_tree: RustDocumentTree) -> Self {
        DocumentTree {
            root: Some(rust_tree.root.into()),
            metadata: Some(rust_tree.metadata.into()),
        }
    }
}

impl From<crate::layout::DocumentMetadata> for DocumentMetadata {
    fn from(rust_meta: crate::layout::DocumentMetadata) -> Self {
        DocumentMetadata {
            total_elements: rust_meta.total_elements as i32,
            heading_count: rust_meta.heading_count as i32,
            table_count: rust_meta.table_count as i32,
        }
    }
}

impl From<RustSectionNode> for SectionNode {
    fn from(rust_node: RustSectionNode) -> Self {
        let mut children = Vec::new();
        for item in rust_node.children {
            children.push(item.into());
        }

        SectionNode {
            id: rust_node.id,
            title: rust_node.title,
            level: rust_node.level as u32,
            xml_path: rust_node.xml_path,
            children,
        }
    }
}

impl From<RustSectionItem> for SectionItem {
    fn from(rust_item: RustSectionItem) -> Self {
        match rust_item {
            RustSectionItem::Subsection(node) => SectionItem {
                item: Some(section_item::Item::Subsection(node.into())),
            },
            RustSectionItem::Content(section) => SectionItem {
                item: Some(section_item::Item::Content(section.into())),
            },
        }
    }
}