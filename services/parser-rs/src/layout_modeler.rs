// src/layout_modeler.rs
use crate::parser::DocumentSection;
use crate::layout::{DocumentTree, DocumentMetadata, SectionNode, SectionItem};

// 私有临时类型（必须在模块顶层定义）
#[derive(Debug, Clone)]
struct TempSectionNode {
    id: i32,
    title: String,
    level: u8,
    xml_path: String,
    children: Vec<TempSectionItem>,
}

#[derive(Debug, Clone)]
enum TempSectionItem {
    Subsection(usize),
    Content(DocumentSection),
}

pub struct LayoutModeler;

impl LayoutModeler {
    pub fn build_tree(elements: Vec<DocumentSection>) -> DocumentTree {
        let mut sections: Vec<TempSectionNode> = vec![TempSectionNode {
            id: 0,
            title: "Root".to_string(),
            level: 0,
            xml_path: "document.xml#root".to_string(),
            children: Vec::new(),
        }];

        let mut stack: Vec<usize> = vec![0];
        let mut heading_count = 0;
        let mut table_count = 0;

        for element in &elements {
            match &element.element_type {
                crate::parser::ElementType::Heading(level) => {
                    heading_count += 1;
                    let new_level = *level;

                    while stack.len() > 1 {
                        let last_idx = *stack.last().unwrap();
                        if sections[last_idx].level < new_level {
                            break;
                        }
                        stack.pop();
                    }

                    let new_section = TempSectionNode {
                        id: element.id,
                        title: element.raw_text.clone(),
                        level: new_level,
                        xml_path: element.xml_path.clone(),
                        children: Vec::new(),
                    };

                    let new_idx = sections.len();
                    sections.push(new_section);

                    let parent_idx = *stack.last().unwrap();
                    sections[parent_idx].children.push(TempSectionItem::Subsection(new_idx));
                    stack.push(new_idx);
                }
                crate::parser::ElementType::Table => {
                    table_count += 1;
                    let content = element.clone();
                    if let Some(&parent_idx) = stack.last() {
                        sections[parent_idx].children.push(TempSectionItem::Content(content));
                    }
                }
                _ => {
                    let content = element.clone();
                    if let Some(&parent_idx) = stack.last() {
                        sections[parent_idx].children.push(TempSectionItem::Content(content));
                    }
                }
            }
        }

        let root = Self::convert_to_final(&sections, 0);
        let metadata = DocumentMetadata {
            total_elements: elements.len(),
            heading_count,
            table_count,
        };

        DocumentTree {
            root,
            metadata,
        }
    }

    fn convert_to_final(sections: &[TempSectionNode], idx: usize) -> SectionNode {
        let section = &sections[idx];
        let mut children = Vec::new();

        for item in &section.children {
            match item {
                TempSectionItem::Subsection(child_idx) => {
                    children.push(SectionItem::Subsection(
                        Self::convert_to_final(sections, *child_idx)
                    ));
                }
                TempSectionItem::Content(content) => {
                    children.push(SectionItem::Content(content.clone()));
                }
            }
        }

        SectionNode {
            id: section.id,
            title: section.title.clone(),
            level: section.level,
            xml_path: section.xml_path.clone(),
            children,
        }
    }
}