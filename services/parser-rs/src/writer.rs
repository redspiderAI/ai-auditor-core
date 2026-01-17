use anyhow::Result;

/// Writer is responsible for writing annotations / comments back into the original .docx XML
pub trait Writer {
    /// Apply a set of issues/comments to the original document and produce a new file at `out_path`.
    fn write_annotations<P: AsRef<std::path::Path>>(
        &self,
        original: P,
        out_path: P,
        issues: &[crate::grpc::Issue],
    ) -> Result<()>;
}

/// Placeholder writer that will be implemented using XML injection into word/comments.xml
pub struct DocxWriter;

impl Writer for DocxWriter {
    fn write_annotations<P: AsRef<std::path::Path>>( 
        &self,
        _original: P,
        _out_path: P,
        _issues: &[crate::grpc::Issue],
    ) -> Result<()> {
        // TODO: implement injection of w:commentReference and word/comments.xml edits
        Ok(())
    }
}
