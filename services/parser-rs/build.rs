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

    // Ensure `protoc` is available or provide clear instructions.
    if std::process::Command::new("protoc").arg("--version").output().is_err() {
        eprintln!("protoc not found: please install protoc and ensure it's on PATH.\nYou can download from https://github.com/protocolbuffers/protobuf/releases or set the PROTOC env var to the protoc binary path.");
        std::process::exit(1);
    }

    tonic_build::configure()
        .build_server(true)
        .compile(&[proto], &[includes])?;

    Ok(())
}
