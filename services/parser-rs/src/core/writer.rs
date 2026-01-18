use anyhow::Result;
use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use std::sync::atomic::{AtomicI32, Ordering};
use zip::{ZipArchive, write::FileOptions};
use std::fs;

static COMMENT_ID_COUNTER: AtomicI32 = AtomicI32::new(1);

/// Writer is responsible for writing annotations / comments back into the original .docx XML
pub trait Writer {
    /// Apply a set of issues/comments to the original document and produce a new file at `out_path`.
    fn write_annotations<P: AsRef<Path>>(
        &self,
        original: P,
        out_path: P,
        issues: &[crate::grpc::Issue],
    ) -> Result<()>;
}

/// DocxWriter implementation that injects comments into .docx files
pub struct DocxWriter;

impl Writer for DocxWriter {
    fn write_annotations<P: AsRef<Path>>(
        &self,
        original: P,
        out_path: P,
        issues: &[crate::grpc::Issue],
    ) -> Result<()> {
        use tempfile::TempDir;
        use zip::write::FileOptions;

        let original_path = original.as_ref();
        let output_path = out_path.as_ref();

        // Create temporary directory to extract the original docx
        let temp_dir = TempDir::new()?;
        let extracted_dir = temp_dir.path();

        // Extract the original docx
        let original_file = File::open(original_path)?;
        let mut archive = ZipArchive::new(original_file)?;

        // Extract all files to temp directory
        for i in 0..archive.len() {
            let mut file = archive.by_index(i)?;
            let outpath = extracted_dir.join(file.mangled_name());

            if file.name().ends_with('/') {
                std::fs::create_dir_all(&outpath)?;
            } else {
                if let Some(parent) = outpath.parent() {
                    std::fs::create_dir_all(parent)?;
                }
                let mut outfile = File::create(&outpath)?;
                std::io::copy(&mut file, &mut outfile)?;
            }
        }

        // Update document.xml with comment references
        self.update_document_xml_with_comments(extracted_dir, issues)?;

        // Update comments.xml with actual comment content
        self.update_comments_xml(extracted_dir, issues)?;

        // Update relationships to include comments relationship
        self.update_relationships(extracted_dir)?;

        // Repackage the docx
        let output_file = File::create(output_path)?;
        let mut zip_writer = zip::ZipWriter::new(output_file);

        // Walk through extracted directory and add files to zip
        self.add_files_to_zip(&mut zip_writer, extracted_dir, extracted_dir)?;

        zip_writer.finish()?;

        Ok(())
    }
}

impl DocxWriter {
    /// Updates document.xml with comment references
    fn update_document_xml_with_comments(&self, extracted_dir: &Path, issues: &[crate::grpc::Issue]) -> Result<()> {
        use roxmltree::Document;

        let doc_xml_path = extracted_dir.join("word").join("document.xml");
        if !doc_xml_path.exists() {
            return Ok(()); // Skip if document.xml doesn't exist
        }

        // Read the document.xml
        let mut doc_content = String::new();
        File::open(&doc_xml_path)?.read_to_string(&mut doc_content)?;
        
        // Parse the XML
        let doc = Document::parse(&doc_content)?;
        
        // Create a map of section_id to issues for quick lookup
        let mut issues_map: HashMap<i32, Vec<&crate::grpc::Issue>> = HashMap::new();
        for issue in issues {
            issues_map.entry(issue.section_id).or_default().push(issue);
        }

        // For now, we'll just append comments to the end of the document
        // A more sophisticated implementation would insert comments at specific locations
        let mut modified_content = doc_content.clone();
        
        // Add a simple comment reference at the end of the document body
        if !issues.is_empty() {
            let comment_refs: Vec<String> = issues.iter().map(|issue| {
                let comment_id = COMMENT_ID_COUNTER.fetch_add(1, Ordering::SeqCst);
                format!(r#"<w:commentReference w:id="{}"/>"#, comment_id)
            }).collect();
            
            // Find the closing body tag and insert comment references before it
            if let Some(pos) = modified_content.rfind("</w:body>") {
                let insertion_point = pos;
                let comment_refs_str = comment_refs.join(" ");
                modified_content.insert_str(insertion_point, &format!(" {}", comment_refs_str));
            }
        }

        // Write the modified content back to document.xml
        fs::write(&doc_xml_path, modified_content)?;

        Ok(())
    }

    /// Updates comments.xml with actual comment content
    fn update_comments_xml(&self, extracted_dir: &Path, issues: &[crate::grpc::Issue]) -> Result<()> {
        use std::io::BufWriter;

        let word_dir = extracted_dir.join("word");
        let comments_path = word_dir.join("comments.xml");

        // Create or update comments.xml
        let mut comments_content = if comments_path.exists() {
            let mut content = String::new();
            File::open(&comments_path)?.read_to_string(&mut content)?;
            content
        } else {
            // Create a basic comments.xml structure
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:comments xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
</w:comments>"#.to_string()
        };

        // Add comments to the XML
        for issue in issues {
            let comment_id = COMMENT_ID_COUNTER.load(Ordering::SeqCst) - issues.iter().position(|i| i.id == issue.id).unwrap_or(0) as i32;
            let comment_xml = format!(
                r#"
    <w:comment w:id="{}" w:author="AI Auditor" w:date="{}">
        <w:p>
            <w:pPr>
                <w:pStyle w:val="CommentText"/>
            </w:pPr>
            <w:r>
                <w:rPr>
                    <w:rStyle w:val="CommentReference"/>
                </w:rPr>
                <w:t>{}</w:t>
            </w:r>
        </w:p>
    </w:comment>"#,
                comment_id,
                chrono::Utc::now().format("%Y-%m-%dT%H:%M:%SZ"),
                html_escape::encode_text(&issue.message)
            );

            // Insert the comment into the comments section
            if let Some(pos) = comments_content.rfind("</w:comments>") {
                comments_content.insert_str(pos, &comment_xml);
            }
        }

        // Write the updated comments back to file
        fs::write(&comments_path, comments_content)?;

        Ok(())
    }

    /// Updates relationships to include comments relationship
    fn update_relationships(&self, extracted_dir: &Path) -> Result<()> {
        let rels_dir = extracted_dir.join("word").join("_rels");
        let rels_path = rels_dir.join("document.xml.rels");

        // Create the relationships directory if it doesn't exist
        fs::create_dir_all(&rels_dir)?;

        let mut rels_content = if rels_path.exists() {
            let mut content = String::new();
            File::open(&rels_path)?.read_to_string(&mut content)?;
            content
        } else {
            // Create a basic relationships structure
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>"#.to_string()
        };

        // Check if comments relationship already exists
        if !rels_content.contains("comments.xml") {
            // Add the comments relationship
            let comment_rel = r#"
    <Relationship Id="rIdComments" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments" Target="comments.xml"/>"#;

            if let Some(pos) = rels_content.rfind("</Relationships>") {
                rels_content.insert_str(pos, comment_rel);
            }
        }

        // Write the updated relationships back to file
        fs::write(&rels_path, rels_content)?;

        Ok(())
    }

    /// Helper function to add files to the zip archive
    fn add_files_to_zip<W: Write + std::io::Seek>(
        &self,
        zip: &mut zip::ZipWriter<W>,
        dir: &Path,
        base: &Path,
    ) -> Result<()> {
        use walkdir::WalkDir;

        for entry in WalkDir::new(dir) {
            let entry = entry?;
            let path = entry.path();

            if path.is_file() {
                let relative_path = path.strip_prefix(base)?
                    .to_str()
                    .ok_or_else(|| anyhow::anyhow!("Invalid path"))?;

                zip.start_file(relative_path, FileOptions::default())?;
                let mut file = File::open(path)?;
                std::io::copy(&mut file, zip)?;
            }
        }

        Ok(())
    }
}