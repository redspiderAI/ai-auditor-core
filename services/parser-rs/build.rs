// build.rs
use std::path::Path;

fn main() {
    let proto_file = "proto/document.proto";

    // 验证 proto 文件存在
    if !Path::new(proto_file).exists() {
        panic!("Proto file not found: {}", proto_file);
    }

    println!("cargo:rerun-if-changed={}", proto_file);

    // 配置 prost-build
    let mut config = prost_build::Config::new();
    config.out_dir("src");

    // Try to compile protos, but don't panic if protoc isn't available
    if let Err(e) = config.compile_protos(&["proto/document.proto"], &["proto/"]) {
        eprintln!("Warning: Failed to compile protos: {}. Using pre-generated file.", e);
        // If protoc isn't available, we'll use the pre-generated file
    }
}
    // If the optional feature `with-proto` is not enabled, skip proto generation.
    if std::env::var("CARGO_FEATURE_WITH_PROTO").is_err() {
        // Feature not enabled: silently skip proto code generation to avoid noisy warnings.
        return Ok(());
    }

    // Ensure `protoc` is available. Honor PROTOC env override on Windows/other platforms.
    let protoc_bin = std::env::var("PROTOC").unwrap_or_else(|_| "protoc".to_string());
    if std::process::Command::new(&protoc_bin).arg("--version").output().is_err() {
        eprintln!("protoc not found (checked {:?}). Install protoc and ensure it's on PATH or set PROTOC env to the protoc binary.\nDownload: https://github.com/protocolbuffers/protobuf/releases", protoc_bin);
        std::process::exit(1);
    }

    tonic_build::configure()
        .build_server(true)
        .compile(&[proto], &[includes])?;

    Ok(())
}
