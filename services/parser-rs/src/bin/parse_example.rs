use parser_rs::core::parser::{DocxParser, Parser};
use std::path::Path;

fn main() -> anyhow::Result<()> {
    let parser = DocxParser;
    let data_dir = Path::new("./data");
    if !data_dir.exists() {
        println!("No data/ directory found. Create services/parser-rs/data and place .docx files there.");
        return Ok(());
    }

    for entry in std::fs::read_dir(data_dir)? {
        let entry = entry?;
        let path = entry.path();
        if let Some(ext) = path.extension() {
            if ext == "docx" {
                println!("Parsing {}", path.display());
                let tree = parser.parse(&path)?;
                println!("Found {} sections", tree.sections.len());
                for s in tree.sections.iter().take(5) {
                    println!("[{}] {}", s.id, s.raw_text);
                }
            }
        }
    }

    Ok(())
}
