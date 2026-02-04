use anyhow::Result;
use std::fs::File;
use std::net::SocketAddr;
use std::path::{Path, PathBuf};

#[cfg(feature = "with-proto")]
use parser_rs::grpc_server;
use parser_rs::parser::DocxParser;
use parser_rs::parser::Parser;
use parser_rs::layout::DocumentTree;

#[tokio::main]
async fn main() -> Result<()> {
    // One-shot batch parse to data/output before starting servers
    if let Err(e) = run_batch_once() {
        eprintln!("batch parse failed: {}", e);
    }

    // Ports can be overridden by env vars to match docker-compose expectations
    let grpc_port = std::env::var("RUST_GRPC_PORT").unwrap_or_else(|_| "52051".into());
    let health_port = std::env::var("RUST_HEALTH_PORT").unwrap_or_else(|_| "50051".into());

fn main() -> anyhow::Result<()> {
    // Run in CLI mode (default)
    let test_file = "tests/sample.docx";

    let parser = DocxParser::new();
    let sections = parser.parse(test_file)?;
    let document_tree = LayoutModeler::build_tree(sections);

    // Start gRPC server (tonic) when compiled with `with-proto` feature; otherwise keep a dummy listener.
    #[cfg(feature = "with-proto")]
    let grpc_server = tokio::spawn(async move {
        let svc = grpc_server::make_server();
        let addr = grpc_addr;
        println!("starting tonic gRPC on {}", addr);
        if let Err(e) = tonic::transport::Server::builder().add_service(svc).serve(addr).await {
            eprintln!("gRPC server error: {}", e);
        }
    ];

    if std::path::Path::new(test_file).exists() {
        match crate::core::comment_writer::inject_comments(
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

fn repo_root() -> Result<PathBuf> {
    let manifest = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let services_dir = manifest.parent().ok_or_else(|| anyhow::anyhow!("no parent for manifest"))?;
    let root = services_dir.parent().ok_or_else(|| anyhow::anyhow!("no parent for services"))?;
    Ok(root.to_path_buf())
}

fn discover_targets(input_root: &Path) -> Vec<PathBuf> {
    let mut v = Vec::new();
    if let Ok(entries) = std::fs::read_dir(input_root) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                v.extend(discover_targets(&path));
            } else if let Some(name) = path.file_name().and_then(|s| s.to_str()) {
                if name.to_lowercase().ends_with("毕业论文.docx") {
                    v.push(path);
                }
            }
        }
    }
    v.sort();
    v
}

fn write_output(output_root: &Path, input_root: &Path, source: &Path, tree: &DocumentTree) -> Result<()> {
    let rel_parent = source.parent().and_then(|p| p.strip_prefix(input_root).ok()).unwrap_or_else(|| Path::new(""));
    let target_dir = output_root.join(rel_parent);
    std::fs::create_dir_all(&target_dir)?;

    let stem = source.file_stem().and_then(|s| s.to_str()).unwrap_or("output");
    let out_path = target_dir.join(format!("{}_parsed.json", stem));

    let mut file = File::create(out_path)?;
    serde_json::to_writer_pretty(&mut file, tree)?;
    Ok(())
}

fn run_batch_once() -> Result<()> {
    let root = repo_root()?;
    let input_root = root.join("data").join("input");
    let output_root = root.join("data").join("output");

    if !input_root.exists() {
        eprintln!("input root not found: {}", input_root.display());
        return Ok(());
    }

    let docs = discover_targets(&input_root);
    if docs.is_empty() {
        eprintln!("no thesis docx found under {}", input_root.display());
        return Ok(());
    }

    let parser = DocxParser;
    for doc in docs {
        match parser.parse(&doc) {
            Ok(tree) => {
                if let Err(e) = write_output(&output_root, &input_root, &doc, &tree) {
                    eprintln!("write output failed for {}: {}", doc.display(), e);
                } else {
                    println!("parsed -> output: {}", doc.display());
                }
            }
            Err(e) => {
                eprintln!("parse failed for {}: {}", doc.display(), e);
            }
        }
    }

    Ok(())
}
