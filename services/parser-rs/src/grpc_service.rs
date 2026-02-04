use tonic::{transport::Server, Request, Response, Status};
use tonic::async_trait;
use std::future::Future;
use std::pin::Pin;
use std::boxed::Box;

// Import the generated protobuf code
// If protoc isn't available, we'll use the pre-generated document.rs
pub mod document {
    include!("document.rs");
}

use document::*;

type BoxFuture<T> = Pin<Box<dyn Future<Output = T> + Send + 'static>>;

#[derive(Debug, Default)]
pub struct DocumentParserService {}

#[async_trait]
impl document_parser_server::DocumentParser for DocumentParserService {
    type ParseDocumentFuture = BoxFuture<Result<Response<ParseDocumentResponse>, Status>>;
    type InjectCommentsFuture = BoxFuture<Result<Response<InjectCommentsResponse>, Status>>;

    fn parse_document(
        &self,
        request: Request<ParseDocumentRequest>,
    ) -> Self::ParseDocumentFuture {
        Box::pin(async move {
            let req = request.into_inner();
            let file_path = req.file_path;

            // Use the existing DocxParser to parse the document
            use crate::parser::Parser;
            let parser = crate::docx_parser::DocxParser::new();
            match parser.parse(&file_path) {
                Ok(sections) => {
                    // 使用新的转换器将Rust结构转换为protobuf ParsedData
                    let doc_id = std::path::Path::new(&file_path)
                        .file_stem()
                        .unwrap_or_default()
                        .to_string_lossy()
                        .to_string();

                    let title = doc_id.clone(); // 使用文件名作为标题
                    let page_count = 10; // 估算页面数，实际需要从文档中提取

                    let parsed_data = crate::parsed_data_converter::ParsedDataConverter::convert_from_rust_sections(
                        doc_id,
                        sections,
                        title,
                        page_count,
                    );

                    let response = ParseDocumentResponse {
                        success: true,
                        error_message: "".to_string(),
                        parsed_data: Some(parsed_data),
                    };

                    Ok(Response::new(response))
                }
                Err(e) => {
                    let response = ParseDocumentResponse {
                        success: false,
                        error_message: e.to_string(),
                        parsed_data: None,
                    };

                    Ok(Response::new(response))
                }
            }
        })
    }

    fn inject_comments(
        &self,
        request: Request<InjectCommentsRequest>,
    ) -> Self::InjectCommentsFuture {
        Box::pin(async move {
            let req = request.into_inner();

            // Convert the request comments to ErrorItem format
            let error_items: Vec<crate::comment_writer::ErrorItem> = req.comments.into_iter()
                .enumerate()
                .map(|(i, comment)| crate::comment_writer::ErrorItem {
                    paragraph_index: i,
                    comment: comment.text,
                })
                .collect();

            match crate::comment_writer::inject_comments(
                &req.input_file_path,
                error_items,
                &req.output_file_path,
                req.author,
            ) {
                Ok(_) => {
                    let response = InjectCommentsResponse {
                        success: true,
                        error_message: "".to_string(),
                        output_file_path: req.output_file_path,
                    };

                    Ok(Response::new(response))
                }
                Err(e) => {
                    let response = InjectCommentsResponse {
                        success: false,
                        error_message: e.to_string(),
                        output_file_path: "".to_string(),
                    };

                    Ok(Response::new(response))
                }
            }
        })
    }
}

pub async fn start_grpc_server(address: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let addr = address.parse()?;
    let service = DocumentParserService::default();

    println!("🚀 Starting gRPC server on {}", address);

    Server::builder()
        .add_service(document_parser_server::DocumentParserServer::new(service))
        .serve(addr)
        .await?;

    Ok(())
}