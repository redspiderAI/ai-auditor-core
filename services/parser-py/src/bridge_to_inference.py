"""Simple bridge: call parser-py ParseDocument then send to inference-py AuditRules.

Usage:
  # Activate venv first:  & services\parser-py\.venv\Scripts\Activate.ps1
  # Ensure parser and inference servers are running on given addresses.
  PARSER_ADDR=localhost:50051 INFER_ADDR=localhost:50052 uv run python -m src.bridge_to_inference "E:\\github\\ai-auditor-core\\data\\input\\18通信2_1800301208_李良循\\18通信2_李良循_毕业论文.docx"

Env:
  PARSER_ADDR: host:port for parser-py (default localhost:50051)
  INFER_ADDR:  host:port for inference-py (default localhost:50052)
"""
from __future__ import annotations

import os
import sys
import grpc

from src import auditor_pb2, auditor_pb2_grpc


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python -m src.bridge_to_inference <doc_path>")
        return 1

    doc_path = sys.argv[1]
    parser_addr = os.environ.get("PARSER_ADDR", "localhost:50051")
    infer_addr = os.environ.get("INFER_ADDR", "localhost:50052")

    # Step 1: call parser-py
    with grpc.insecure_channel(parser_addr) as ch:
        parser_stub = auditor_pb2_grpc.DocumentAuditorStub(ch)
        parsed = parser_stub.ParseDocument(
            auditor_pb2.ParseRequest(file_path=doc_path, template_type="GB/T7714")
        )

    # Step 2: call inference-py AnalyzeSemantics with parsed sections
    with grpc.insecure_channel(infer_addr) as ch:
        infer_stub = auditor_pb2_grpc.DocumentAuditorStub(ch)
        audit_resp = infer_stub.AnalyzeSemantics(
            auditor_pb2.SemanticRequest(sections=parsed.sections, model_version="bridge")
        )

    print("=== Parsed sections ===")
    for sec in parsed.sections:
        print(f"[{sec.section_id}] {sec.type} -> {sec.text[:80]}")
    print("=== Inference issues (AnalyzeSemantics) ===")
    for issue in audit_resp.issues:
        print(f"{issue.code} (severity={issue.severity}) section={issue.section_id}: {issue.message}")
    print(f"score_impact={audit_resp.score_impact}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
