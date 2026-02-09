// src/layout_modeler.rs
use crate::core::layout::{DocumentMetadata, DocumentTree, PositionInfo, SectionItem, SectionNode};
use crate::{DocumentSection, ElementType};
use std::collections::HashMap;

const DEFAULT_PAGE_WIDTH_PT: f64 = 612.0; // 8.5in * 72pt
const DEFAULT_PAGE_HEIGHT_PT: f64 = 792.0; // 11in * 72pt
const DEFAULT_MARGIN_PT: f64 = 72.0; // 1in
const DEFAULT_DPI: f64 = 96.0;

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

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct FontKey {
    family: String,
    size_tenths: u32,
}

#[derive(Debug, Clone, Copy)]
struct FontMetrics {
    avg_char_width: f64,
    line_height: f64,
}

#[derive(Debug, Default)]
struct FontMetricsCache {
    dpi: f64,
    cache: HashMap<FontKey, FontMetrics>,
}

impl FontMetricsCache {
    fn new(dpi: f64) -> Self {
        Self {
            dpi,
            cache: HashMap::new(),
        }
    }

    fn metrics(&mut self, family: &str, size_pt: f64) -> FontMetrics {
        let key = FontKey {
            family: family.to_ascii_lowercase(),
            size_tenths: (size_pt * 10.0).round() as u32,
        };

        if let Some(metrics) = self.cache.get(&key) {
            return *metrics;
        }

        // Basic heuristic: avg char width ~ 0.55 * font size, line height ~ 1.2 * font size
        let avg_char_width = size_pt * 0.55;
        let line_height = size_pt * 1.2;
        let metrics = FontMetrics {
            avg_char_width,
            line_height,
        };

        self.cache.insert(key, metrics);
        metrics
    }
}

struct LayoutCursor {
    y: f64,
    page: u32,
}

impl LayoutCursor {
    fn new(margin_pt: f64) -> Self {
        Self {
            y: margin_pt,
            page: 1,
        }
    }

    fn reserve_block(&mut self, height: f64, page_height: f64, margin: f64) -> (u32, f64) {
        if self.y + height > page_height - margin {
            self.page += 1;
            self.y = margin;
        }

        let current_page = self.page;
        let start_y = self.y;
        self.y += height;
        (current_page, start_y)
    }
}

pub struct LayoutModeler {
    page_width_pt: f64,
    page_height_pt: f64,
    margin_pt: f64,
    dpi: f64,
    font_cache: FontMetricsCache,
}

impl Default for LayoutModeler {
    fn default() -> Self {
        Self::new()
    }
}

impl LayoutModeler {
    pub fn new() -> Self {
        Self {
            page_width_pt: DEFAULT_PAGE_WIDTH_PT,
            page_height_pt: DEFAULT_PAGE_HEIGHT_PT,
            margin_pt: DEFAULT_MARGIN_PT,
            dpi: DEFAULT_DPI,
            font_cache: FontMetricsCache::new(DEFAULT_DPI),
        }
    }

    pub fn with_page_size(mut self, width_pt: f64, height_pt: f64) -> Self {
        self.page_width_pt = width_pt;
        self.page_height_pt = height_pt;
        self
    }

    pub fn with_margin(mut self, margin_pt: f64) -> Self {
        self.margin_pt = margin_pt;
        self
    }

    pub fn with_dpi(mut self, dpi: f64) -> Self {
        self.dpi = dpi;
        self.font_cache.dpi = dpi;
        self
    }

    pub fn build_tree(&mut self, elements: Vec<DocumentSection>) -> DocumentTree {
        let mut sections: Vec<TempSectionNode> = vec![TempSectionNode {
            id: 0,
            title: "Root".to_string(),
            level: 0,
            xml_path: "document.xml#root".to_string(),
            children: Vec::new(),
        }];

        let mut stack: Vec<usize> = vec![0];
        let mut heading_count = 0usize;
        let mut table_count = 0usize;
        let mut positions: HashMap<i32, PositionInfo> = HashMap::new();
        let mut cursor = LayoutCursor::new(self.margin_pt);

        for element in elements.into_iter() {
            let pos = self.estimate_position(&element, &mut cursor);
            positions.insert(element.id, pos);

            match element.element_type {
                ElementType::Heading(level) => {
                    heading_count += 1;
                    let new_level = level;

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
                    sections[parent_idx]
                        .children
                        .push(TempSectionItem::Subsection(new_idx));
                    stack.push(new_idx);
                }
                ElementType::Table => {
                    table_count += 1;
                    if let Some(&parent_idx) = stack.last() {
                        sections[parent_idx]
                            .children
                            .push(TempSectionItem::Content(element));
                    }
                }
                _ => {
                    if let Some(&parent_idx) = stack.last() {
                        sections[parent_idx]
                            .children
                            .push(TempSectionItem::Content(element));
                    }
                }
            }
        }

        let root = Self::convert_to_final(&sections, 0);
        let metadata = DocumentMetadata {
            total_elements: positions.len(),
            heading_count,
            table_count,
            total_pages: Some(cursor.page),
            file_path: None,
            file_size: None,
        };

        DocumentTree::new(root, metadata).with_positions(positions)
    }

    fn convert_to_final(sections: &[TempSectionNode], idx: usize) -> SectionNode {
        let section = &sections[idx];
        let mut children = Vec::new();

        for item in &section.children {
            match item {
                TempSectionItem::Subsection(child_idx) => {
                    children.push(SectionItem::Subsection(Self::convert_to_final(
                        sections, *child_idx,
                    )));
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

    fn estimate_position(
        &mut self,
        element: &DocumentSection,
        cursor: &mut LayoutCursor,
    ) -> PositionInfo {
        let font_size_pt = self.parse_font_size(&element.formatting).unwrap_or(12.0);
        let font_family = element
            .formatting
            .get("font-family")
            .map(String::as_str)
            .unwrap_or("Times New Roman");

        let metrics = self.font_cache.metrics(font_family, font_size_pt);
        let line_spacing = self.parse_line_spacing(&element.formatting).unwrap_or(1.2);
        let line_height = metrics.line_height * line_spacing;

        let indent = self
            .parse_pt(element.formatting.get("indent-left"))
            .unwrap_or(0.0);
        let content_width = (self.page_width_pt - 2.0 * self.margin_pt - indent).max(0.0);

        let text_len = element.raw_text.chars().count() as f64;
        let width =
            (metrics.avg_char_width * text_len).min(content_width.max(metrics.avg_char_width));

        let (page_number, y) =
            cursor.reserve_block(line_height, self.page_height_pt, self.margin_pt);
        let x = self.margin_pt + indent;

        PositionInfo {
            x,
            y,
            width,
            height: line_height,
            page_number: Some(page_number),
        }
    }

    fn parse_font_size(&self, formatting: &HashMap<String, String>) -> Option<f64> {
        formatting
            .get("font-size")
            .and_then(|v| self.parse_pt(Some(v)))
    }

    fn parse_line_spacing(&self, formatting: &HashMap<String, String>) -> Option<f64> {
        formatting
            .get("line-spacing")
            .and_then(|v| v.trim().parse::<f64>().ok())
    }

    fn parse_pt(&self, value: Option<&String>) -> Option<f64> {
        value.and_then(|v| {
            let stripped = v.trim();
            let numeric = stripped.trim_end_matches("pt");
            numeric.parse::<f64>().ok()
        })
    }
}
