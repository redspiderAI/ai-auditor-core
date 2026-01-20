use crate::docx_parser::ParsedParagraph;
use regex::Regex;
use serde::Serialize;

#[derive(Serialize)]
pub struct Section {
    pub section_id: u32,
    #[serde(rename = "type")]
    pub kind: String,
    pub text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub level: Option<u32>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub citations: Vec<String>,
    pub properties: Properties,
}

#[derive(Serialize)]
pub struct Properties {
    pub font_size: f32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub font_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub first_line_indent: Option<f32>,
}

#[derive(Serialize)]
pub struct OutputProtocol {
    pub doc_id: String,
    pub metadata: Metadata,
    pub sections: Vec<Section>,
    pub references: Vec<Reference>,
}

#[derive(Serialize)]
pub struct Metadata {
    pub headers: Vec<String>,
    pub footers: Vec<String>,
    pub global_style: serde_json::Value,
}

#[derive(Serialize)]
pub struct Reference {
    pub ref_id: String,
    pub raw_text: String,
}

fn is_reference_section_end(text: &str) -> bool {
    let end_keywords = ["作者简介", "学位论文数据集", "致谢", "附录", "Acknowledgements"];
    end_keywords.iter().any(|&kw| text.contains(kw))
}

pub fn convert_to_protocol(
    paragraphs: Vec<ParsedParagraph>,
    headers: Vec<String>,
    footers: Vec<String>,
    doc_id: String,
) -> OutputProtocol {
    let mut sections = Vec::new();
    let mut references = Vec::new();
    let mut in_references = false;
    let mut section_id: u32 = 1;

    let citation_pattern = Regex::new(r"\[\d+(?:[-,]\d+)*\]").unwrap();

    // 找参考文献起始
    let total = paragraphs.len();
    let ref_start_index = paragraphs
        .iter()
        .enumerate()
        .find(|(i, p)| {
            p.text.contains("参考文献")
                && p.text.len() <= 20
                && (p.style.contains("Heading 1") || p.style.contains("标题1"))
                && *i > total * 7 / 10
        })
        .map(|(i, _)| i);

    for (i, p) in paragraphs.into_iter().enumerate() {
        let text = p.text;
        let style = p.style;
        let font_size = p.font_size_pt;
        let font_name = p.font_name;
        let first_line_indent = p.first_line_indent_pt;

        if Some(i) == ref_start_index {
            in_references = true;
            continue;
        }

        if in_references {
            if style == "Heading 1" || is_reference_section_end(&text) {
                in_references = false;
                let section = create_section_from_paragraph(
                    &text,
                    &style,
                    font_size,
                    font_name,
                    first_line_indent,
                    &citation_pattern,
                    section_id,
                );
                sections.push(section);
                section_id += 1;
                continue;
            }

            if style == "List Paragraph" && !text.trim().is_empty() {
                if text.trim().chars().next().map_or(false, |c| c.is_alphabetic() || c == '[') {
                    let ref_id = format!("[{}]", references.len() + 1);
                    let raw_with_id = format!("{} {}", ref_id, text);
                    references.push(Reference {
                        ref_id,
                        raw_text: raw_with_id,
                    });
                }
            }
        } else {
            let section = create_section_from_paragraph(
                &text,
                &style,
                font_size,
                font_name,
                first_line_indent,
                &citation_pattern,
                section_id,
            );
            sections.push(section);
            section_id += 1;
        }
    }

    OutputProtocol {
        doc_id,
        metadata: Metadata {
            headers,
            footers,
            global_style: serde_json::json!({}),
        },
        sections,
        references,
    }
}

fn create_section_from_paragraph(
    text: &str,
    style: &str,
    font_size: f32,
    font_name: Option<String>,
    first_line_indent: Option<f32>,
    citation_pattern: &Regex,
    section_id: u32,
) -> Section {
    let is_heading = style.contains("Heading 1")
        || style.contains("Heading 2")
        || style.contains("标题1")
        || style.contains("标题2");

    if is_heading {
        let level = if style.contains("2") { 2 } else { 1 };
        Section {
            section_id,
            kind: "heading".to_string(),
            text: text.to_string(),
            level: Some(level),
            citations: vec![],
            properties: Properties {
                font_size,
                font_name,
                first_line_indent,
            },
        }
    } else {
        let citations: Vec<String> = citation_pattern
            .find_iter(text)
            .map(|m| m.as_str().to_string())
            .collect();

        Section {
            section_id,
            kind: "paragraph".to_string(),
            text: text.to_string(),
            level: None,
            citations,
            properties: Properties {
                font_size,
                font_name,
                first_line_indent,
            },
        }
    }
}