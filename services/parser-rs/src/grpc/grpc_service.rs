use tonic::{transport::Server, Request, Response, Status};
use tonic::async_trait;
use std::future::Future;
use std::pin::Pin;
use std::boxed::Box;

pub mod document {
    include!("document.rs");
}

use document::*;

use crate::core::parser::{DocxParser, Parser};
use crate::utils::comment_writer;

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

            let parser = DocxParser;
            match parser.parse(&file_path) {
                Ok(document_tree) => {
                    let response = ParseDocumentResponse {
                        success: true,
                        error_message: "".to_string(),
                        parsed_data: Some(ParsedData {
                            document_tree: Some(document_tree.into()),
                        }),
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

            let error_items: Vec<comment_writer::ErrorItem> = req.comments.into_iter()
                .enumerate()
                .map(|(i, comment)| comment_writer::ErrorItem {
                    paragraph_index: i,
                    comment: comment.text,
                })
                .collect();

            match comment_writer::inject_comments(
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
