"""示例 gRPC 服务：实现 DocumentAuditorServicer 并启动服务器

使用方法（在 services/inference-py 下）：

```powershell
# 通过 uv 运行
uv run -m src.grpc_server

```
"""
from concurrent import futures
import logging
import time

import grpc

from src.protos import auditor_pb2, auditor_pb2_grpc


class DocumentAuditorServicer(auditor_pb2_grpc.DocumentAuditorServicer):
    """轻量示例实现：返回基础占位响应供集成与测试使用。"""

    def ParseDocument(self, request, context):
        metadata = auditor_pb2.DocumentMetadata(
            title=f"parsed:{request.file_path}",
            page_count=0,
            margin_top=0.0,
            margin_bottom=0.0,
        )
        parsed = auditor_pb2.ParsedData(
            doc_id=(request.file_path or "unknown"),
            metadata=metadata,
        )
        return parsed

    def AuditRules(self, request, context):
        resp = auditor_pb2.AuditResponse()
        resp.score_impact = 0.0
        return resp

    def AnalyzeSemantics(self, request, context):
        resp = auditor_pb2.AuditResponse()
        resp.score_impact = 0.0
        return resp


def serve(host: str = "0.0.0.0", port: int = 50051) -> None:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    auditor_pb2_grpc.add_DocumentAuditorServicer_to_server(DocumentAuditorServicer(), server)
    bind_addr = f"{host}:{port}"
    server.add_insecure_port(bind_addr)
    server.start()
    logging.info("gRPC server started on %s", bind_addr)
    try:
        while True:
            time.sleep(60)
    except KeyboardInterrupt:
        logging.info("Shutting down gRPC server")
        server.stop(0)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    serve()
