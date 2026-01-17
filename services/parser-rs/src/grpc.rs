// Minimal gRPC message/stub types to be expanded into proper `prost`/`tonic` generated code.
// In future, place .proto in `proto/` and generate into `proto_generated/`.

#[derive(Debug, Clone)]
pub struct Issue {
    pub id: i64,
    pub message: String,
    pub section_id: i32,
}

/// Placeholder for tonic service skeleton. Real service should be generated from proto.
pub mod service {
    use tonic::{Request, Response, Status};

    #[derive(Debug, Default)]
    pub struct ParserService;

    impl ParserService {
        pub async fn health_check(
            &self,
            _req: Request<()> ,
        ) -> Result<Response<String>, Status> {
            Ok(Response::new("ok".to_string()))
        }
    }
}
