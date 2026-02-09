import argparse
import json
import logging
import os
import re
from pathlib import Path
from typing import Tuple

import grpc

from src import grpc_server
from src.grpc_server import DocumentAuditorServicer
from src.protos import auditor_pb2, auditor_pb2_grpc

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT_ROOT = REPO_ROOT / "data" / "input"
DEFAULT_OUTPUT_ROOT = REPO_ROOT / "data" / "output"
TARGET_PATTERN = re.compile(r"毕业论文\.docx$", re.IGNORECASE)


def _parse_addr_env(env_value: str) -> Tuple[str, int]:
    """解析类似 'inference-py:8123' 或 '0.0.0.0:8123' 或 '8123' 的地址，返回 (host, port).
    如果只给端口，host 保持默认 '127.0.0.1'.
    """
    default_host = "127.0.0.1"
    if not env_value:
        return default_host, 0
    parts = env_value.rsplit(":", 1)
    if len(parts) == 1:
        # 只有一个部分，可能是端口或名称
        try:
            port = int(parts[0])
            return default_host, port
        except ValueError:
            return parts[0], 0
    host_part, port_part = parts[0], parts[1]
    try:
        port = int(port_part)
    except ValueError:
        port = 0
    # 如果 host_part 看起来像容器名（例如 inference-py），仍然把它作为 host 字符串使用
    return host_part or default_host, port


def _issue_to_dict(issue: auditor_pb2.Issue) -> dict:
    severity_name = auditor_pb2.Severity.Name(issue.severity)
    return {
        "code": issue.code,
        "message": issue.message,
        "section_id": issue.section_id,
        "severity": severity_name,
        "suggestion": issue.suggestion,
        "original_snippet": issue.original_snippet,
    }


def _write_output(
    output_root: Path,
    input_root: Path,
    source_path: Path,
    parsed: auditor_pb2.ParsedData,
    resp: auditor_pb2.AuditResponse,
) -> None:
    rel_parent = source_path.parent.relative_to(input_root)
    target_dir = output_root.joinpath(rel_parent)
    target_dir.mkdir(parents=True, exist_ok=True)

    out_path = target_dir.joinpath(f"{source_path.stem}_audit.json")

    metadata = None
    if parsed.HasField("metadata"):
        metadata = {
            "title": parsed.metadata.title,
            "page_count": parsed.metadata.page_count,
            "margin_top": parsed.metadata.margin_top,
            "margin_bottom": parsed.metadata.margin_bottom,
        }

    payload = {
        "doc_id": parsed.doc_id,
        "source": str(source_path),
        "sections": len(parsed.sections),
        "metadata": metadata,
        "issues": [_issue_to_dict(i) for i in resp.issues],
        "score_impact": resp.score_impact,
    }

    with out_path.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
    logging.info("wrote %s (issues=%d)", out_path, len(resp.issues))


def _discover_targets(input_root: Path):
    return sorted(
        p for p in input_root.rglob("*.docx") if p.is_file() and TARGET_PATTERN.search(p.name)
    )


def run_batch(parser_addr: str, input_root: Path, output_root: Path) -> None:
    input_root = input_root.resolve()
    output_root = output_root.resolve()

    docs = _discover_targets(input_root)
    if not docs:
        logging.warning("no matching docx files under %s", input_root)
        return

    logging.info("found %d target document(s) under %s", len(docs), input_root)

    servicer = DocumentAuditorServicer()

    with grpc.insecure_channel(parser_addr) as channel:
        stub = auditor_pb2_grpc.DocumentAuditorStub(channel)

        for doc_path in docs:
            logging.info("parsing %s via parser-rs @ %s", doc_path, parser_addr)
            try:
                parsed = stub.ParseDocument(
                    auditor_pb2.ParseRequest(file_path=str(doc_path), template_type="")
                )
            except Exception as exc:  # pragma: no cover - network/runtime failure
                logging.error("parse failed for %s: %s", doc_path, exc)
                continue

            audit_req = auditor_pb2.AuditRequest()
            audit_req.data.CopyFrom(parsed)

            try:
                resp = servicer.AuditRules(audit_req, context=None)
            except Exception as exc:  # pragma: no cover - model/runtime failure
                logging.error("audit failed for %s: %s", doc_path, exc)
                continue

            _write_output(output_root, input_root, doc_path, parsed, resp)

    logging.info("batch run complete; results in %s", output_root)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1", help="gRPC bind host")
    parser.add_argument("--port", type=int, default=50051, help="gRPC bind port")
    parser.add_argument("--serve", action="store_true", help="Start gRPC server instead of batch run")
    parser.add_argument("--parser-addr", default=os.environ.get("PARSER_GRPC_ADDR"), help="parser-rs gRPC address host:port")
    parser.add_argument("--input-root", default=None, help="Override input root (defaults to repo data/input)")
    parser.add_argument("--output-root", default=None, help="Override output root (defaults to repo data/output)")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO)

    # 默认执行批处理：扫描 data/input 中仅以 “毕业论文.docx” 结尾的文件，通过 parser-rs 解析后运行审查
    if not args.serve:
        parser_addr = args.parser_addr or os.environ.get("RUST_GRPC_ADDR") or "127.0.0.1:52051"
        input_root = Path(args.input_root) if args.input_root else DEFAULT_INPUT_ROOT
        output_root = Path(args.output_root) if args.output_root else DEFAULT_OUTPUT_ROOT
        logging.info(
            "Running batch mode: parser=%s input=%s output=%s",
            parser_addr,
            input_root,
            output_root,
        )
        run_batch(parser_addr=parser_addr, input_root=input_root, output_root=output_root)
        return

    # 优先读取环境变量 `PY_INFERENCE_ADDR`（格式示例: inference-py:8123 或 127.0.0.1:8123 或 8123）
    env_addr = os.environ.get("PY_INFERENCE_ADDR")
    env_host, env_port = _parse_addr_env(env_addr) if env_addr else (None, 0)

    host = env_host if env_host and env_host != "inference-py" else args.host
    port = env_port if env_port and env_port > 0 else args.port

    logging.info("Starting inference-py gRPC server on %s:%s", host, port)
    grpc_server.serve(host=host, port=port)


if __name__ == "__main__":
    main()
