// src/parsed_data_converter.rs
use crate::parser::{DocumentSection as RustDocumentSection, ElementType as RustElementType};
use crate::document::{ParsedData, DocumentMetadata, Section, Reference};
use std::collections::HashMap;

pub struct ParsedDataConverter;

impl ParsedDataConverter {
    pub fn convert_from_rust_sections(
        doc_id: String,
        rust_sections: Vec<RustDocumentSection>,
        title: String,
        page_count: i32,
    ) -> ParsedData {
        let mut sections = Vec::new();
        let mut references = Vec::new();

        for section in rust_sections.into_iter() {
            // 尝试从文本中提取引用
            if section.raw_text.contains("[") && section.raw_text.contains("]") {
                // 简单的引用提取逻辑
                let refs = Self::extract_references_from_text(&section.raw_text);
                references.extend(refs);
            }

            let section_type = match section.element_type {
                RustElementType::Heading(_) => "heading".to_string(),
                RustElementType::Paragraph => "paragraph".to_string(),
                RustElementType::Table => "table".to_string(),
                RustElementType::Equation => "equation".to_string(),
            };

            let level = match section.element_type {
                RustElementType::Heading(lvl) => lvl as i32,
                _ => 0,
            };

            let mut props = HashMap::new();
            for (key, value) in section.formatting {
                props.insert(key, value);
            }

            let converted_section = Section {
                section_id: section.id,
                r#type: section_type,
                level,
                text: section.raw_text,
                props,
            };

            sections.push(converted_section);
        }

        ParsedData {
            doc_id,
            metadata: Some(DocumentMetadata {
                title,
                page_count,
                margin_top: 1.0,
                margin_bottom: 1.0,
            }),
            sections,
            references,
        }
    }

    fn extract_references_from_text(text: &str) -> Vec<Reference> {
        let mut references = Vec::new();

        // 简单的引用提取 - 查找 [数字] 格式的引用
        let chars: Vec<char> = text.chars().collect();
        let mut i = 0;

        while i < chars.len() {
            if chars[i] == '[' {
                let start = i;
                let mut j = i + 1;

                // 查找匹配的 ']'
                while j < chars.len() && chars[j] != ']' {
                    if chars[j].is_ascii_digit() || chars[j] == ',' || chars[j] == ' ' {
                        j += 1;
                    } else {
                        break;
                    }
                }

                if j < chars.len() && chars[j] == ']' {
                    // 找到一个引用
                    let ref_text: String = chars[start..=j].iter().collect();

                    // 尝试找到引用的完整文本
                    let raw_text = Self::find_reference_text(text, &ref_text);

                    references.push(Reference {
                        ref_id: ref_text,
                        raw_text,
                        is_valid_format: true,
                    });

                    i = j + 1;
                } else {
                    i += 1;
                }
            } else {
                i += 1;
            }
        }

        references
    }

    fn find_reference_text(full_text: &str, ref_id: &str) -> String {
        // 简单的策略：返回包含引用ID的句子
        let sentences: Vec<&str> = full_text.split(['.', '!', '?']).collect();

        for sentence in sentences {
            if sentence.contains(ref_id) {
                // 返回包含引用的句子，加上引用ID
                return format!("{}{}", ref_id, sentence.trim());
            }
        }

        // 如果没找到，返回引用ID本身
        ref_id.to_string()
    }
}