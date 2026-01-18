use anyhow::Result;
use std::net::SocketAddr;

#[tokio::main]
async fn main() -> Result<()> {
    // Check if we're running in document processing mode or server mode
    let args: Vec<String> = std::env::args().collect();

    if args.len() > 1 && args[1] == "process" {
        // Run document processing mode
        run_document_processing().await
    } else {
        // Run server mode (default)
        run_server_mode().await
    }
}

async fn run_document_processing() -> Result<()> {
    println!("Starting document processing mode...");

    // Process documents from input directory
    match parser_rs::utils::document_processor::run_document_processing() {
        Ok(_) => {
            println!("Document processing completed successfully!");
        },
        Err(e) => {
            eprintln!("Failed to process documents: {}", e);
            return Err(e);
        }
    }

    Ok(())
}

async fn run_server_mode() -> Result<()> {
    // Ports can be overridden by env vars to match docker-compose expectations
    let grpc_port = std::env::var("RUST_GRPC_PORT").unwrap_or_else(|_| "52051".into());
    let health_port = std::env::var("RUST_HEALTH_PORT").unwrap_or_else(|_| "50051".into());

    let grpc_addr: SocketAddr = format!("0.0.0.0:{}", grpc_port).parse()?;
    let health_addr: SocketAddr = format!("0.0.0.0:{}", health_port).parse()?;

    println!("parser-rs starting: grpc={} health={}", grpc_addr, health_addr);

    // Start gRPC server (tonic) when compiled with `with-proto` feature; otherwise keep a dummy listener.
    #[cfg(feature = "with-proto")]
    let grpc_server = tokio::spawn(async move {
        let svc = crate::grpc_server::make_server();
        let addr = grpc_addr;
        println!("starting tonic gRPC on {}", addr);
        if let Err(e) = tonic::transport::Server::builder().add_service(svc).serve(addr).await {
            eprintln!("gRPC server error: {}", e);
        }
    });

    #[cfg(not(feature = "with-proto"))]
    let grpc_server = tokio::spawn(async move {
        // fallback: keep a plain TCP listener to satisfy healthchecks and port mapping
        match tokio::net::TcpListener::bind(grpc_addr).await {
            Ok(listener) => {
                loop {
                    match listener.accept().await {
                        Ok((_socket, _peer)) => {}
                        Err(e) => { eprintln!("grpc accept error: {}", e); }
                    }
                }
            }
            Err(e) => { eprintln!("failed to bind grpc listener: {}", e); }
        }
    });

    let health_server = tokio::spawn(async move {
        match tokio::net::TcpListener::bind(health_addr).await {
            Ok(listener) => {
                loop {
                    match listener.accept().await {
                        Ok((_socket, _peer)) => {
                            // accept and drop
                        }
                        Err(e) => {
                            eprintln!("health accept error: {}", e);
                        }
                    }
                }
            }
            Err(e) => {
                eprintln!("failed to bind health listener: {}", e);
            }
        }
    });

    // Wait on both tasks (they run forever). If any errors, bubble up.
    let (g, h) = tokio::join!(grpc_server, health_server);
    if let Err(e) = g { eprintln!("grpc server task ended: {:?}", e); }
    if let Err(e) = h { eprintln!("health server task ended: {:?}", e); }

    Ok(())
}
