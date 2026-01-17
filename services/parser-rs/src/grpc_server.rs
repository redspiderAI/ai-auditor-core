#[cfg(feature = "with-proto")]
pub mod proto {
    tonic::include_proto!("academic.auditor");
}

#[cfg(feature = "with-proto")]
use tonic::{Request, Response, Status};

#[cfg(feature = "with-proto")]
use proto::document_auditor_server::{DocumentAuditor, DocumentAuditorServer};

#[cfg(feature = "with-proto")]
use proto::{ParseRequest, ParsedData, DocumentMetadata, Section as ProtoSection, Reference as ProtoReference, AuditResponse};

#[cfg(feature = "with-proto")]
use crate::parser::DocxParser;

#[cfg(feature = "with-proto")]
use crate::layout::DocumentTree;

#[cfg(feature = "with-proto")]
#[derive(Debug, Clone)]
pub struct AuditorService {
    parser: DocxParser,
}

#[cfg(feature = "with-proto")]
impl AuditorService {
    pub fn new() -> Self {
        Self { parser: DocxParser }
    }
}

#[cfg(feature = "with-proto")]
#[tonic::async_trait]
impl DocumentAuditor for AuditorService {
    async fn parse_document(&self, req: Request<ParseRequest>) -> Result<Response<ParsedData>, Status> {
        let req = req.into_inner();
        let path = req.file_path;

        // Parse using our DocxParser
        let tree: DocumentTree = self.parser.parse(path.clone()).map_err(|e| {
            Status::internal(format!("parse error for {}: {}", path, e))
        })?;

        // Map DocumentTree -> ParsedData
        let metadata = DocumentMetadata { title: "".into(), page_count: 0, margin_top: 0.0, margin_bottom: 0.0 };

        let mut sections = Vec::new();
        for s in tree.sections {
            let proto_sec = ProtoSection {
                section_id: s.id,
                r#type: match s.element_type {
                    crate::ElementType::Heading(_) => "heading".into(),
                    crate::ElementType::Paragraph => "paragraph".into(),
                    crate::ElementType::Table => "table".into(),
                    crate::ElementType::Equation => "equation".into(),
                },
                level: match s.element_type { crate::ElementType::Heading(l) => l as i32, _ => 0 },
                text: s.raw_text,
                props: s.formatting,
            };
            sections.push(proto_sec);
        }

        let resp = ParsedData {
            doc_id: path,
            metadata: Some(metadata),
            sections,
            references: Vec::new(),
        };

        Ok(Response::new(resp))
    }

    async fn audit_rules(&self, _req: Request<proto::AuditRequest>) -> Result<Response<AuditResponse>, Status> {
        Ok(Response::new(AuditResponse { issues: Vec::new(), score_impact: 0.0 }))
    }

    async fn analyze_semantics(&self, _req: Request<proto::SemanticRequest>) -> Result<Response<AuditResponse>, Status> {
        Ok(Response::new(AuditResponse { issues: Vec::new(), score_impact: 0.0 }))
    }
}

#[cfg(feature = "with-proto")]
pub fn make_server() -> DocumentAuditorServer<AuditorService> {
    DocumentAuditorServer::new(AuditorService::new())
}

#[cfg(not(feature = "with-proto"))]
pub fn make_server() -> () { }
