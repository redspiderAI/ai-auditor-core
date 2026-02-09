use parser_rs::core::layout::SectionItem;
use parser_rs::core::parser::{DocxParser, Parser};
use std::path::Path;

fn main() -> anyhow::Result<()> {
    let parser = DocxParser;
    let data_dir = Path::new("./data");
    if !data_dir.exists() {
        println!(
            "No data/ directory found. Create services/parser-rs/data and place .docx files there."
        );
        return Ok(());
    }

    for entry in std::fs::read_dir(data_dir)? {
        let entry = entry?;
        let path = entry.path();
        if let Some(ext) = path.extension() {
            if ext == "docx" {
                println!("Parsing {}", path.display());
                let tree = parser.parse(&path)?;
                println!(
                    "Found {} elements (headings: {}, tables: {})",
                    tree.metadata.total_elements,
                    tree.metadata.heading_count,
                    tree.metadata.table_count
                );

                let mut samples = Vec::new();
                collect_content(&tree.root, &mut samples);
                for s in samples.into_iter().take(5) {
                    println!("[{}] {}", s.id, s.raw_text);
                }
            }
        }
    }

    Ok(())
}

fn collect_content<'a>(
    node: &'a parser_rs::core::layout::SectionNode,
    out: &mut Vec<&'a parser_rs::DocumentSection>,
) {
    for child in &node.children {
        match child {
            SectionItem::Subsection(sub) => collect_content(sub, out),
            SectionItem::Content(c) => out.push(c),
        }
    }
}
