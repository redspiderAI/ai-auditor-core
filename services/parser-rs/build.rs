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