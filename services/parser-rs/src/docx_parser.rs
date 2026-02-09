// src/docx_parser.rs
use std::collections::HashMap;
use std::io::Read;
use zip::ZipArchive;
use roxmltree::{Document, Node};
use rayon::prelude::*;
use crate::parser::{DocumentSection, ElementType, Parser};
use memmap2::Mmap;
use std::fs::File;

const W_NS: &str = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
const R_NS: &str = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
const MC_NS: &str = "http://schemas.openxmlformats.org/markup-compatibility/2006";
const WP_NS: &str = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

pub struct DocxParser {
    styles: HashMap<String, StyleDefinition>,
}

#[derive(Debug, Clone)]
struct StyleDefinition {
    name: String,
    based_on: Option<String>,
    paragraph_props: ParagraphProperties,
    run_props: RunProperties,
}

#[derive(Debug, Clone, Default)]
struct ParagraphProperties {
    style: Option<String>,
    spacing: Option<Spacing>,
    ind: Option<Indent>,
}

#[derive(Debug, Clone)]
struct Spacing {
    line: Option<i32>,
    line_rule: Option<String>,
}

#[derive(Debug, Clone)]
struct Indent {
    first_line: Option<i32>,
}

#[derive(Debug, Clone, Default)]
struct RunProperties {
    sz: Option<i32>,
    rfonts: Option<RFonts>,
}

#[derive(Debug, Clone)]
struct RFonts {
    ascii: Option<String>,
    east_asia: Option<String>,
}

impl DocxParser {
    pub fn new() -> Self {
        Self { styles: HashMap::new() }
    }

    fn load_styles(&mut self, archive: &mut ZipArchive<std::fs::File>) -> anyhow::Result<()> {
        let mut styles_xml = String::new();
        if let Ok(mut file) = archive.by_name("word/styles.xml") {
            file.read_to_string(&mut styles_xml)?;
            self.parse_styles_xml(&styles_xml)?;
        }
        Ok(())
    }

    fn parse_styles_xml(&mut self, xml: &str) -> anyhow::Result<()> {
        let doc = roxmltree::Document::parse(xml)?;
        let root = doc.root_element();

        for style_node in root.children().filter(|n| n.tag_name().name() == "style") {
            if let Some(style_id) = style_node.attribute((W_NS, "styleId")) {
                let name = style_node
                    .children()
                    .find(|n| n.tag_name().name() == "name")
                    .and_then(|n| n.attribute((W_NS, "val")))
                    .unwrap_or(style_id);

                let based_on = style_node
                    .children()
                    .find(|n| n.tag_name().name() == "basedOn")
                    .and_then(|n| n.attribute((W_NS, "val")));

                let mut para_props = ParagraphProperties::default();
                let mut run_props = RunProperties::default();

                if let Some(ppr) = style_node.children().find(|n| n.tag_name().name() == "pPr") {
                    para_props = self.parse_paragraph_properties_from_node(ppr);
                }

                if let Some(rpr) = style_node.children().find(|n| n.tag_name().name() == "rPr") {
                    run_props = self.parse_run_properties_from_node(rpr);
                }

                self.styles.insert(
                    style_id.to_string(),
                    StyleDefinition {
                        name: name.to_string(),
                        based_on: based_on.map(|s| s.to_string()),
                        paragraph_props: para_props,
                        run_props,
                    },
                );
            }
        }
        Ok(())
    }

    fn parse_paragraph_properties_from_node(&self, ppr: Node) -> ParagraphProperties {
        let mut props = ParagraphProperties::default();

        for child in ppr.children() {
            match child.tag_name().name() {
                "spacing" => {
                    let line = child.attribute((W_NS, "line")).and_then(|v| v.parse::<i32>().ok());
                    let line_rule = child.attribute((W_NS, "lineRule")).map(|s| s.to_string());
                    props.spacing = Some(Spacing { line, line_rule });
                }
                "ind" => {
                    let first_line = child.attribute((W_NS, "firstLine")).and_then(|v| v.parse::<i32>().ok());
                    props.ind = Some(Indent { first_line });
                }
                "pStyle" => {
                    if let Some(val) = child.attribute((W_NS, "val")) {
                        props.style = Some(val.to_string());
                    }
                }
                _ => {}
            }
        }
        props
    }

    fn parse_run_properties_from_node(&self, rpr: Node) -> RunProperties {
        let mut props = RunProperties::default();

        for child in rpr.children() {
            match child.tag_name().name() {
                "sz" => {
                    if let Some(val) = child.attribute((W_NS, "val")).and_then(|v| v.parse::<i32>().ok()) {
                        props.sz = Some(val);
                    }
                }
                "rFonts" => {
                    let ascii = child.attribute((W_NS, "ascii")).map(|s| s.to_string());
                    let east_asia = child.attribute((W_NS, "eastAsia")).map(|s| s.to_string());
                    props.rfonts = Some(RFonts { ascii, east_asia });
                }
                _ => {}
            }
        }
        props
    }

    fn resolve_style(&self, style_id: &str) -> Option<StyleDefinition> {
        let mut current_style_id = style_id;
        let mut visited_styles = Vec::new();

        // Follow the inheritance chain to avoid circular references
        while let Some(style_def) = self.styles.get(current_style_id) {
            if visited_styles.contains(&current_style_id) {
                // Circular reference detected, break the loop
                break;
            }

            visited_styles.push(current_style_id);

            // If this style doesn't inherit from another, return it
            if let Some(ref base_style_id) = style_def.based_on {
                current_style_id = base_style_id;
            } else {
                // Return a style with inherited properties applied
                return Some(self.apply_inherited_properties(style_def, &visited_styles));
            }
        }

        None
    }

    fn apply_inherited_properties(&self, style_def: &StyleDefinition, inheritance_chain: &[&str]) -> StyleDefinition {
        let mut final_style = style_def.clone();

        // Process inheritance chain in reverse order (most base first)
        for &style_id in inheritance_chain.iter().rev() {
            if let Some(inherited_style) = self.styles.get(style_id) {
                // Apply paragraph properties inheritance
                if final_style.paragraph_props.style.is_none() && inherited_style.paragraph_props.style.is_some() {
                    final_style.paragraph_props.style = inherited_style.paragraph_props.style.clone();
                }

                if final_style.paragraph_props.spacing.is_none() && inherited_style.paragraph_props.spacing.is_some() {
                    final_style.paragraph_props.spacing = inherited_style.paragraph_props.spacing.clone();
                }

                if final_style.paragraph_props.ind.is_none() && inherited_style.paragraph_props.ind.is_some() {
                    final_style.paragraph_props.ind = inherited_style.paragraph_props.ind.clone();
                }

                // Apply run properties inheritance
                if final_style.run_props.sz.is_none() && inherited_style.run_props.sz.is_some() {
                    final_style.run_props.sz = inherited_style.run_props.sz;
                }

                if final_style.run_props.rfonts.is_none() && inherited_style.run_props.rfonts.is_some() {
                    final_style.run_props.rfonts = inherited_style.run_props.rfonts.clone();
                }
            }
        }

        final_style
    }

    fn get_default_style(&self) -> StyleDefinition {
        // Return a default style with common Word defaults
        StyleDefinition {
            name: "Default".to_string(),
            based_on: None,
            paragraph_props: ParagraphProperties {
                style: Some("Normal".to_string()),
                spacing: Some(Spacing {
                    line: Some(240), // 1.15 line spacing in twips
                    line_rule: Some("auto".to_string()),
                }),
                ind: None,
            },
            run_props: RunProperties {
                sz: Some(24), // 12pt font size (24 half-points)
                rfonts: Some(RFonts {
                    ascii: Some("Times New Roman".to_string()),
                    east_asia: Some("宋体".to_string()),
                }),
            },
        }
    }

    fn extract_text_from_node(&self, node: &Node) -> String {
        let mut text = String::new();
        for child in node.descendants() {
            if child.is_text() {
                if let Some(content) = child.text() {
                    text.push_str(content);
                }
            }
        }
        text.replace('\r', "").replace('\n', "")
    }

    fn parse_paragraph(&self, para: &Node, offset: usize, section_id: i32) -> Option<DocumentSection> {
        let text = self.extract_text_from_node(para);
        if text.trim().is_empty() {
            return None;
        }

        // Extract style ID early
        let style_id = para
            .children()
            .find(|n| n.tag_name().name() == "pPr")
            .and_then(|ppr| {
                ppr.children()
                    .find(|n| n.tag_name().name() == "pStyle")
                    .and_then(|ps| ps.attribute((W_NS, "val")))
            })
            .unwrap_or("Normal");

        // Pre-allocate formatting map
        let mut formatting = HashMap::with_capacity(4);

        // Extract paragraph properties in a single pass
        let mut outline_level: Option<u32> = None;
        let mut first_line_indent = None;
        let mut line_spacing = "1.15".to_string(); // Default line spacing

        if let Some(ppr) = para.children().find(|n| n.tag_name().name() == "pPr") {
            for child in ppr.children() {
                match child.tag_name().name() {
                    "ind" => {
                        if let Some(fl) = child.attribute((W_NS, "firstLine")).and_then(|v| v.parse::<i32>().ok()) {
                            first_line_indent = Some(fl as f32 / 20.0);
                        }
                    },
                    "spacing" => {
                        if let Some(line) = child.attribute((W_NS, "line")).and_then(|v| v.parse::<i32>().ok()) {
                            if line >= 1000 {
                                line_spacing = format!("{}pt", line as f32 / 20.0);
                            } else {
                                line_spacing = format!("{}", line as f32 / 240.0);
                            }
                        }
                    },
                    "outlineLvl" => {
                        if let Some(val) = child.attribute((W_NS, "val")) {
                            if let Ok(level) = val.parse::<u32>() {
                                outline_level = Some(level + 1);
                            }
                        }
                    },
                    _ => {}
                }
            }
        }

        // Extract run properties efficiently
        let mut font_size = 12.0;
        let mut font_family = "Times New Roman".to_string(); // Default font

        // Find the first run with font info to avoid processing all runs unnecessarily
        for run in para.descendants().filter(|n| n.tag_name().name() == "r") {
            if let Some(rpr) = run.children().find(|n| n.tag_name().name() == "rPr") {
                // Look for font size
                if font_size == 12.0 {
                    if let Some(sz) = rpr.children().find(|n| n.tag_name().name() == "sz") {
                        if let Some(val) = sz.attribute((W_NS, "val")).and_then(|v| v.parse::<i32>().ok()) {
                            font_size = val as f32 / 2.0;
                        }
                    }
                }

                // Look for font family
                if font_family == "Times New Roman" {
                    if let Some(rfonts) = rpr.children().find(|n| n.tag_name().name() == "rFonts") {
                        if let Some(ascii_font) = rfonts.attribute((W_NS, "ascii")) {
                            font_family = ascii_font.to_string();
                        } else if let Some(east_asia_font) = rfonts.attribute((W_NS, "eastAsia")) {
                            font_family = east_asia_font.to_string();
                        }
                    }
                }

                // Early exit if we found both properties
                if font_size != 12.0 && font_family != "Times New Roman" {
                    break;
                }
            }
        }

        // Resolve style with inheritance and apply defaults
        if let Some(resolved_style) = self.resolve_style(style_id) {
            // Apply font size from style if not set in run
            if font_size == 12.0 {
                if let Some(sz) = resolved_style.run_props.sz {
                    font_size = sz as f32 / 2.0;
                }
            }

            // Apply font family from style if not set in run
            if font_family == "Times New Roman" {
                if let Some(ref fonts) = resolved_style.run_props.rfonts {
                    if let Some(ref ascii) = fonts.ascii {
                        font_family = ascii.clone();
                    } else if let Some(ref east_asia) = fonts.east_asia {
                        font_family = east_asia.clone();
                    }
                }
            }

            // Apply line spacing from style if not set in paragraph
            if line_spacing == "1.15" {
                if let Some(ref spacing) = resolved_style.paragraph_props.spacing {
                    if let Some(line_val) = spacing.line {
                        if line_val >= 1000 {
                            line_spacing = format!("{}pt", line_val as f32 / 20.0);
                        } else {
                            line_spacing = format!("{}", line_val as f32 / 240.0);
                        }
                    }
                }
            }
        } else {
            // Apply default style if no matching style found
            let default_style = self.get_default_style();
            if font_size == 12.0 {
                if let Some(sz) = default_style.run_props.sz {
                    font_size = sz as f32 / 2.0;
                }
            }

            if font_family == "Times New Roman" {
                if let Some(ref fonts) = default_style.run_props.rfonts {
                    if let Some(ref ascii) = fonts.ascii {
                        font_family = ascii.clone();
                    } else if let Some(ref east_asia) = fonts.east_asia {
                        font_family = east_asia.clone();
                    }
                }
            }
        }

        let is_heading = outline_level.is_some() || style_id.starts_with("Heading") || style_id.contains("标题");
        let final_level = outline_level.unwrap_or_else(|| {
            if is_heading {
                style_id.chars().last().and_then(|c| c.to_digit(10)).unwrap_or(1) as u32
            } else {
                0
            }
        });

        // Add formatting properties
        formatting.insert("font-size".to_string(), format!("{}pt", font_size));
        formatting.insert("line-spacing".to_string(), line_spacing);
        formatting.insert("font-family".to_string(), font_family);
        if let Some(indent) = first_line_indent {
            formatting.insert("first-line-indent".to_string(), format!("{}pt", indent));
        }

        let element_type = if is_heading {
            ElementType::Heading(final_level.min(255) as u8)
        } else if para.children().any(|n| n.tag_name().name() == "tbl") {
            ElementType::Table
        } else {
            // 检查是否包含公式（简化）
            if text.contains("OMML") || text.contains("Math") || text.contains("math") {
                ElementType::Equation
            } else {
                ElementType::Paragraph
            }
        };

        Some(DocumentSection {
            id: section_id,
            element_type,
            raw_text: text,
            formatting,
            xml_path: format!("document.xml#offset_{}", offset),
        })
    }

    fn parse_table(&self, tbl: &Node, offset: usize, section_id: i32) -> DocumentSection {
        let text = self.extract_text_from_node(tbl);
        let mut formatting = HashMap::new();
        formatting.insert("font-size".to_string(), "12pt".to_string());
        
        DocumentSection {
            id: section_id,
            element_type: ElementType::Table,
            raw_text: text,
            formatting,
            xml_path: format!("document.xml#offset_{}", offset),
        }
    }
}

impl Parser for DocxParser {
    fn parse(&self, file_path: &str) -> anyhow::Result<Vec<DocumentSection>> {
        // Check file size to determine if we should use memory mapping
        let metadata = std::fs::metadata(file_path)?;
        let file_size = metadata.len();

        // Use memory mapping for large files (> 10MB)
        if file_size > 10 * 1024 * 1024 {
            // Open file with memory mapping for large files
            let file = File::open(file_path)?;
            let mmap = unsafe { Mmap::map(&file)? };

            // Create ZipArchive from the memory-mapped data
            let cursor = std::io::Cursor::new(mmap.to_vec());
            self.parse_archive(cursor)
        } else {
            // Use regular file access for smaller files
            let file = std::fs::File::open(file_path)?;
            let cursor = std::io::Cursor::new(std::fs::read(file_path)?);
            self.parse_archive(cursor)
        }
    }
}

impl DocxParser {
    fn parse_archive<R: std::io::Read + std::io::Seek>(&self, reader: R) -> anyhow::Result<Vec<DocumentSection>> {
        let mut archive = ZipArchive::new(reader)?;

        let mut parser_with_styles = DocxParser::new();
        // Load styles from the archive
        let mut styles_xml = String::new();
        if let Ok(mut file) = archive.by_name("word/styles.xml") {
            file.read_to_string(&mut styles_xml)?;
            parser_with_styles.parse_styles_xml(&styles_xml)?;
        }

        let mut doc_xml_bytes = Vec::new();
        archive.by_name("word/document.xml")?.read_to_end(&mut doc_xml_bytes)?;
        let doc_xml = String::from_utf8(doc_xml_bytes)?;
        let doc = Document::parse(&doc_xml)?;
        let root = doc.root_element();

        let mut section_id = 1i32;

        let elements: Vec<_> = root
            .descendants()
            .enumerate()
            .filter_map(|(offset, node)| {
                let current_id = section_id;
                section_id += 1;
                match node.tag_name().name() {
                    "p" => parser_with_styles.parse_paragraph(&node, offset, current_id),
                    "tbl" => Some(parser_with_styles.parse_table(&node, offset, current_id)),
                    _ => None,
                }
            })
            .collect();

        let processed: Vec<DocumentSection> = elements
            .par_iter()
            .cloned()
            .collect();

        Ok(processed)
    }
}