use crate::core::layout::{DocumentTree, SectionItem, SectionNode};
use crate::core::parser::UniversalParser;
use anyhow::Result;
use log::{debug, error, info, warn};
use regex::Regex;
use serde::Serialize;
use serde_json;
use std::fs;
use std::fs::File;
use std::io::BufWriter;
use std::path::Path;

pub struct DocumentProcessor {
    parser: UniversalParser,
}

impl DocumentProcessor {
    pub fn new() -> Self {
        Self {
            parser: UniversalParser::new(),
        }
    }

    /// Process all documents in the input directory and write results to output directory
    pub fn process_documents<P: AsRef<Path>>(&self, input_dir: P, output_dir: P) -> Result<()> {
        let input_path = input_dir.as_ref();
        let output_path = output_dir.as_ref();

        info!(
            "Starting to process documents from: {}",
            input_path.display()
        );

        fs::create_dir_all(output_path)?;
        info!("Output directory prepared: {}", output_path.display());

        let entries = fs::read_dir(input_path)
            .map_err(|e| anyhow::anyhow!("Failed to read input directory: {}", e))?;

        for entry in entries {
            let entry =
                entry.map_err(|e| anyhow::anyhow!("Failed to read directory entry: {}", e))?;
            let file_path = entry.path();

            if file_path.is_dir() {
                info!("Processing directory: {:?}", file_path.file_name());

                match self.process_student_directory(&file_path, &output_path) {
                    Ok(_) => info!(
                        "Successfully processed directory: {:?}",
                        file_path.file_name()
                    ),
                    Err(e) => {
                        error!(
                            "Failed to process directory {:?}: {}",
                            file_path.file_name(),
                            e
                        );
                        continue;
                    }
                }
            } else {
                debug!("Skipping non-directory entry: {:?}", file_path.file_name());
            }
        }

        info!("Completed processing all documents");
        Ok(())
    }

    /// Process a single student directory containing their documents
    fn process_student_directory<P1: AsRef<Path>, P2: AsRef<Path>>(
        &self,
        student_dir: P1,
        output_dir: P2,
    ) -> Result<()> {
        let student_path = student_dir.as_ref();
        let output_path = output_dir.as_ref();

        info!("Processing student directory: {}", student_path.display());

        let student_output_dir = output_path.join(student_path.file_name().unwrap_or_default());
        fs::create_dir_all(&student_output_dir)?;
        debug!("Created output directory: {}", student_output_dir.display());

        // Only process .docx files whose filename ends with "毕业论文"
        let docx_name_re = Regex::new(r"(?i)毕业论文\.docx$").expect("valid regex");

        let entries = fs::read_dir(student_path)
            .map_err(|e| anyhow::anyhow!("Failed to read student directory: {}", e))?;

        for entry in entries {
            let entry = entry.map_err(|e| anyhow::anyhow!("Failed to read file entry: {}", e))?;
            let file_path = entry.path();

            if !file_path.is_file() {
                debug!("Skipping non-file entry: {:?}", file_path.file_name());
                continue;
            }

            let file_ext = file_path
                .extension()
                .and_then(|ext| ext.to_str())
                .unwrap_or("")
                .to_lowercase();

            if file_ext != "docx" {
                debug!("Skipping non-docx file: {}", file_path.display());
                continue;
            }

            let file_name = file_path.file_name().and_then(|n| n.to_str()).unwrap_or("");

            if !docx_name_re.is_match(file_name) {
                debug!("Skipping docx not matching target pattern: {}", file_name);
                continue;
            }

            info!("Processing file: {}", file_path.display());

            match self.parser.parse(&file_path) {
                Ok(document_tree) => {
                    let parsed_output_path = student_output_dir.join(format!(
                        "{}_parsed.json",
                        file_path.file_stem().unwrap_or_default().to_string_lossy()
                    ));

                    if let Err(e) = self.save_parsed_json(&document_tree, &parsed_output_path) {
                        warn!(
                            "Failed to save parsed JSON for {:?}: {}",
                            file_path.file_name(),
                            e
                        );
                    } else {
                        info!("Saved parsed JSON: {}", parsed_output_path.display());
                    }
                }
                Err(e) => {
                    error!(
                        "Failed to parse document {:?}: {}",
                        file_path.file_name(),
                        e
                    );
                }
            }
        }

        Ok(())
    }

    /// Save parsed tree into JSON file with the expected schema
    fn save_parsed_json<P: AsRef<Path>>(
        &self,
        document_tree: &crate::core::layout::DocumentTree,
        output_path: P,
    ) -> Result<()> {
        let output_doc = self.build_output_document(document_tree);
        let file = File::create(output_path.as_ref())
            .map_err(|e| anyhow::anyhow!("Failed to create parsed JSON file: {}", e))?;

        let writer = BufWriter::new(file);
        serde_json::to_writer_pretty(writer, &output_doc)
            .map_err(|e| anyhow::anyhow!("Failed to write parsed JSON: {}", e))?;

        Ok(())
    }

    fn build_output_document(&self, document_tree: &DocumentTree) -> OutputDocument {
        fn convert(node: &SectionNode) -> OutputSectionNode {
            let mut children = Vec::new();

            for child in &node.children {
                match child {
                    SectionItem::Subsection(sub) => {
                        children.push(OutputSectionItem::Subsection {
                            subsection: convert(sub),
                        });
                    }
                    SectionItem::Content(content) => {
                        children.push(OutputSectionItem::Content {
                            content: content.clone(),
                        });
                    }
                }
            }

            OutputSectionNode {
                id: node.id,
                title: node.title.clone(),
                level: node.level as u32,
                xml_path: node.xml_path.clone(),
                children,
            }
        }

        OutputDocument {
            root: convert(&document_tree.root),
            metadata: OutputMetadata {
                total_elements: document_tree.metadata.total_elements,
                heading_count: document_tree.metadata.heading_count,
                table_count: document_tree.metadata.table_count,
            },
        }
    }
}

#[derive(Serialize)]
struct OutputDocument {
    root: OutputSectionNode,
    metadata: OutputMetadata,
}

#[derive(Serialize)]
struct OutputMetadata {
    total_elements: usize,
    heading_count: usize,
    table_count: usize,
}

#[derive(Serialize)]
struct OutputSectionNode {
    id: i32,
    title: String,
    level: u32,
    xml_path: String,
    children: Vec<OutputSectionItem>,
}

#[derive(Serialize)]
#[serde(untagged)]
enum OutputSectionItem {
    Subsection { subsection: OutputSectionNode },
    Content { content: crate::DocumentSection },
}

// Example usage function
pub fn run_document_processing() -> Result<()> {
    if env_logger::try_init().is_err() {
        // Logger already initialized
    }

    info!("Initializing document processor...");
    let processor = DocumentProcessor::new();

    let current_dir = std::env::current_dir().unwrap();
    let repo_root = current_dir.parent().unwrap().parent().unwrap();
    let input_dir = repo_root.join("data").join("input");
    let output_dir = repo_root.join("data").join("output");

    info!(
        "Starting document processing from '{}' to '{}'",
        input_dir.display(),
        output_dir.display()
    );

    processor.process_documents(input_dir, output_dir)?;

    info!("Document processing completed!");

    Ok(())
}
