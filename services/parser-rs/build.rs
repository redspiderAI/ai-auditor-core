fn main() -> Result<(), Box<dyn std::error::Error>> {
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR")?;
    // proto path relative to services/parser-rs -> ../../shared/protos/auditor.proto
    use std::path::PathBuf;
    let proto: PathBuf = PathBuf::from(&manifest_dir)
        .join("..")
        .join("..")
        .join("shared")
        .join("protos")
        .join("auditor.proto");

    let includes: PathBuf = PathBuf::from(&manifest_dir).join("..").join("..").join("shared").join("protos");

    println!("cargo:rerun-if-changed={}", proto.display());

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
