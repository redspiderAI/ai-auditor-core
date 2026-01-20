// src/main.rs
use prost::Message; // 用于 encode_to_vec
mod document;
mod parser;
mod docx_parser;
mod layout;
mod layout_modeler;
mod protobuf_converter;
mod mmap_reader;
mod comment_writer;

use docx_parser::DocxParser;
use parser::Parser;
use layout_modeler::LayoutModeler;
use std::fs;

fn main() -> anyhow::Result<()> {
    // Run in CLI mode (default)
    let test_file = "tests/sample.docx";

    let parser = DocxParser::new();
    let sections = parser.parse(test_file)?;
    let document_tree = LayoutModeler::build_tree(sections);

    let parsed_data = document::ParsedData {
        document_tree: Some(document_tree.into()),
    };

    // 只生成 Protobuf 二进制
    let protobuf_bytes = parsed_data.encode_to_vec();
    fs::write("output.protobin", &protobuf_bytes)?;

    println!("✅ Protobuf output saved to output.protobin");

    // Test comment injection functionality
    let error_items = vec![
        crate::comment_writer::ErrorItem {
            paragraph_index: 0,
            comment: "This is a test comment".to_string(),
        }
    ];

    if std::path::Path::new(test_file).exists() {
        match crate::comment_writer::inject_comments(
            test_file,
            error_items,
            "output_with_comments.docx",
            "AI Auditor".to_string(),
        ) {
            Ok(_) => println!("✅ Comments injected successfully"),
            Err(e) => eprintln!("⚠️  Failed to inject comments: {}", e),
        }
    } else {
        println!("⚠️  Test file not found, skipping comment injection test");
    }

    Ok(())
}