use anyhow::Result;

fn main() -> Result<()> {
    env_logger::init();

    let args: Vec<String> = std::env::args().collect();
    let mode = args.get(1).map(String::as_str).unwrap_or("");

    match mode {
        "process" => {
            parser_rs::utils::document_processor::run_document_processing()?;
            println!("Document processing completed successfully.");
        }
        _ => {
            println!("parser_rs binary ready. Use 'process' to process documents from data/input to data/output.");
        }
    }

    Ok(())
}
