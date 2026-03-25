import argparse
import asyncio
import json
import logging
import os
import re
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Tuple

import grpc
import uvicorn
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from src import grpc_server
from src.grpc_server import DocumentAuditorServicer
from src.protos import auditor_pb2, auditor_pb2_grpc
from src.semantic_detection import ComprehensiveSemanticDetector
from src.fact_checking import ReferenceFactChecker

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT_ROOT = REPO_ROOT / "data" / "input"
DEFAULT_OUTPUT_ROOT = REPO_ROOT / "data" / "output"
# FastAPI 上传文件的暂存目录
UPLOAD_ROOT = Path(tempfile.gettempdir()) / "ai_auditor_uploads"
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


def _build_summary(
    resp: auditor_pb2.AuditResponse,
    started_at: float,
    ended_at: float,
    text_chars: int,
    references_found: int,
    mode: str = "EMERGENCY_AI_ONLY",
) -> dict:
    return {
        "total_issues": len(resp.issues),
        "score": resp.score_impact,
        "mode": mode,
        "message": "当前处于快速 AI 审查模式，物理格式检查（字号/行距）已跳过。",
        "started_at": started_at,
        "ended_at": ended_at,
        "duration_ms": int((ended_at - started_at) * 1000),
        "text_chars": text_chars,
        "references_found": references_found,
    }


def _to_payload(
    parsed: auditor_pb2.ParsedData,
    resp: auditor_pb2.AuditResponse,
    source_path: Path,
    summary: dict | None,
) -> dict:
    references = [
        {
            "ref_id": ref.ref_id,
            "raw_text": ref.raw_text,
            "is_valid_format": ref.is_valid_format,
        }
        for ref in parsed.references
    ]

    metadata = None
    if parsed.HasField("metadata"):
        metadata = {
            "title": parsed.metadata.title,
            "page_count": parsed.metadata.page_count,
            "margin_top": parsed.metadata.margin_top,
            "margin_bottom": parsed.metadata.margin_bottom,
        }

    return {
        "doc_id": parsed.doc_id,
        "source": str(source_path),
        "sections": len(parsed.sections),
        "metadata": metadata,
        "references": references,
        "issues": [_issue_to_dict(i) for i in resp.issues],
        "score_impact": resp.score_impact,
        "summary": summary,
    }


def _write_output(
    output_root: Path,
    input_root: Path,
    source_path: Path,
    parsed: auditor_pb2.ParsedData,
    resp: auditor_pb2.AuditResponse,
    summary: dict | None,
) -> None:
    rel_parent = source_path.parent.relative_to(input_root)
    target_dir = output_root.joinpath(rel_parent)
    target_dir.mkdir(parents=True, exist_ok=True)

    out_path = target_dir.joinpath(f"{source_path.stem}_audit.json")
    payload = _to_payload(parsed, resp, source_path, summary)

    with out_path.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
    logging.info("wrote %s (issues=%d)", out_path, len(resp.issues))


def _discover_targets(input_root: Path):
    return sorted(
        p for p in input_root.rglob("*.docx") if p.is_file() and TARGET_PATTERN.search(p.name)
    )


def _compute_score_impact(issues: list[auditor_pb2.Issue]) -> float:
    """Compute weighted score impact consistent with gRPC servicer logic."""
    high = sum(1 for issue in issues if issue.severity == auditor_pb2.HIGH)
    medium = sum(1 for issue in issues if issue.severity == auditor_pb2.MEDIUM)
    low = sum(1 for issue in issues if issue.severity == auditor_pb2.LOW)
    total_weight = high * 0.7 + medium * 0.3 + low * 0.1
    return min(total_weight / max(len(issues), 1), 1.0) if issues else 0.0


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
            started_at = time.time()
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

            ended_at = time.time()
            text_chars = sum(len(s.text) for s in parsed.sections)
            references_found = len(parsed.references)
            summary = _build_summary(
                resp,
                started_at=started_at,
                ended_at=ended_at,
                text_chars=text_chars,
                references_found=references_found,
                mode="EMERGENCY_AI_ONLY",
            )

            _write_output(output_root, input_root, doc_path, parsed, resp, summary)

    logging.info("batch run complete; results in %s", output_root)


def _audit_single_sync(servicer: DocumentAuditorServicer, parser_addr: str, doc_path: Path) -> Tuple[auditor_pb2.ParsedData, auditor_pb2.AuditResponse]:
    """Parse a single document via parser-rs and run audit (synchronous)."""
    try:
        with grpc.insecure_channel(parser_addr) as channel:
            stub = auditor_pb2_grpc.DocumentAuditorStub(channel)
            parsed = stub.ParseDocument(
                auditor_pb2.ParseRequest(file_path=str(doc_path), template_type="")
            )
    except grpc.RpcError as exc:
        parsed = _fallback_parsed(doc_path)
        logging.warning("parser-rs unavailable at %s: %s; using fallback parsed data", parser_addr, exc)

    audit_req = auditor_pb2.AuditRequest()
    audit_req.data.CopyFrom(parsed)
    resp = servicer.AuditRules(audit_req, context=None)
    return parsed, resp


async def _audit_single_async(
    servicer: DocumentAuditorServicer, parser_addr: str, doc_path: Path
) -> Tuple[auditor_pb2.ParsedData, auditor_pb2.AuditResponse, dict]:
    """Async wrapper for HTTP path to avoid nested event loop errors."""
    started_at = time.time()
    text_chars = 0
    references_found = 0
    try:
        with grpc.insecure_channel(parser_addr) as channel:
            stub = auditor_pb2_grpc.DocumentAuditorStub(channel)
            parsed = stub.ParseDocument(
                auditor_pb2.ParseRequest(file_path=str(doc_path), template_type="")
            )
    except grpc.RpcError as exc:
        parsed = _fallback_parsed(doc_path)
        logging.warning("parser-rs unavailable at %s: %s; using fallback parsed data", parser_addr, exc)
        text = _extract_docx_text(doc_path)
        references = _extract_references_from_text(text) if text else []
        references_found = len(references)
        if text:
            text_chars = len(text)
            resp = await _build_resp_from_text(text, references, getattr(servicer, "fact_checker", None))
        else:
            def _run_audit():
                audit_req = auditor_pb2.AuditRequest()
                audit_req.data.CopyFrom(parsed)
                return servicer.AuditRules(audit_req, context=None)

            resp = await asyncio.to_thread(_run_audit)
    else:
        # 当 parser-rs 返回的参考文献为空时，尝试直接从 docx 文本提取引用，避免缺失参考校验
        if not parsed.references:
            text_for_refs = _extract_docx_text(doc_path)
            extracted_refs = _extract_references_from_text(text_for_refs)
            for ref_text in extracted_refs:
                ref_msg = parsed.references.add()
                ref_msg.raw_text = ref_text
            if not text_chars:
                text_chars = len(text_for_refs) if text_for_refs else 0

        def _run_audit():
            audit_req = auditor_pb2.AuditRequest()
            audit_req.data.CopyFrom(parsed)
            return servicer.AuditRules(audit_req, context=None)

        resp = await asyncio.to_thread(_run_audit)
        if not text_chars:
            text_chars = sum(len(s.text) for s in parsed.sections)
        references_found = len(parsed.references)

    ended_at = time.time()
    summary = _build_summary(
        resp,
        started_at=started_at,
        ended_at=ended_at,
        text_chars=text_chars,
        references_found=references_found,
    )
    return parsed, resp, summary


def _ensure_upload_root() -> Path:
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)
    return UPLOAD_ROOT


def _save_upload(file: UploadFile) -> Path:
    """Persist uploaded file to temp dir and return path."""
    temp_dir = _ensure_upload_root()
    target = temp_dir / file.filename
    with target.open("wb") as f:
        content = file.file.read()
        f.write(content)
    return target


def _fallback_parsed(doc_path: Path) -> auditor_pb2.ParsedData:
    """Build a minimal ParsedData when parser-rs is unavailable."""
    metadata = auditor_pb2.DocumentMetadata(
        title=f"fallback:{doc_path.name}",
        page_count=0,
        margin_top=0.0,
        margin_bottom=0.0,
    )
    parsed = auditor_pb2.ParsedData(doc_id=str(doc_path), metadata=metadata)
    parsed.sections.append(
        auditor_pb2.Section(
            section_id=1,
            type="paragraph",
            level=0,
            text=f"自动回退占位内容：{doc_path.name}",
        )
    )
    return parsed


def _extract_docx_text(doc_path: Path) -> str:
    """Lightweight docx text extraction without external deps."""
    if not doc_path.suffix.lower() == ".docx":
        return ""
    try:
        with zipfile.ZipFile(doc_path) as zf:
            with zf.open("word/document.xml") as f:
                xml_bytes = f.read()
        # crude tag strip
        text = re.sub(r"<[^>]+>", "", xml_bytes.decode("utf-8", errors="ignore"))
        return text
    except Exception as exc:  # pragma: no cover - fallback only
        logging.warning("docx extraction failed for %s: %s", doc_path, exc)
        return ""


def _extract_references_from_text(text: str) -> list[str]:
    """Naive reference extraction for fallback mode using bracket patterns."""
    refs: set[str] = set()
    line_pattern = re.compile(r"\[\d+\].+")
    for line in text.splitlines():
        match = line_pattern.search(line)
        if match:
            candidate = match.group(0).strip()
            if candidate:
                refs.add(candidate)
    return list(refs)


async def _build_resp_from_text(
    text: str, references: list[str], fact_checker: ReferenceFactChecker | None
) -> auditor_pb2.AuditResponse:
    """Run semantic + reference checks in fallback mode."""
    detector = ComprehensiveSemanticDetector()
    issues = detector.detect_issues(text)
    resp = auditor_pb2.AuditResponse()
    resp.issues.extend(issues)

    if references and fact_checker:
        ref_results = await fact_checker.check_references(references)
        for result in ref_results:
            if not result.is_valid:
                resp.issues.extend(result.issues)

    resp.score_impact = _compute_score_impact(list(resp.issues))
    return resp


def create_app(parser_addr: str) -> FastAPI:
    app = FastAPI(title="AI Auditor (FastAPI)")

    servicer = DocumentAuditorServicer()

    @app.post("/audit")
    async def audit(file: UploadFile = File(...), file_path: str | None = None):
        """接收 docx 文件（上传或传路径）并返回审查 JSON。"""
        try:
            if file_path:
                path = Path(file_path)
                if not path.is_file():
                    raise HTTPException(status_code=400, detail="file_path 不存在")
            else:
                if file.content_type not in {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", None}:
                    raise HTTPException(status_code=400, detail="仅支持 .docx 上传")
                path = _save_upload(file)

            parsed, resp, summary = await _audit_single_async(servicer, parser_addr, path)
            payload = _to_payload(parsed, resp, path, summary)
            return JSONResponse(payload)
        except HTTPException:
            raise
        except Exception as exc:  # pragma: no cover - runtime failure
            logging.exception("audit failed: %s", exc)
            raise HTTPException(status_code=500, detail=f"审查失败: {exc}")

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app


def main():
    parser = argparse.ArgumentParser(description="AI Auditor entrypoint")
    parser.add_argument("--mode", choices=["fastapi", "batch", "grpc"], default="fastapi", help="运行模式：fastapi(默认)、batch、grpc")
    parser.add_argument("--host", default="0.0.0.0", help="FastAPI/GRPC bind host")
    parser.add_argument("--port", type=int, default=8000, help="FastAPI/GRPC bind port")
    parser.add_argument("--parser-addr", default=os.environ.get("PARSER_GRPC_ADDR") or "127.0.0.1:52051", help="parser-rs gRPC address host:port")
    parser.add_argument("--input-root", default=None, help="batch 模式输入目录")
    parser.add_argument("--output-root", default=None, help="batch 模式输出目录")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO)

    # On Windows default to disabling embedded Milvus Lite (can be re-enabled by clearing the env)
    if os.name == "nt" and not os.environ.get("MILVUS_LITE_DISABLED"):
        os.environ["MILVUS_LITE_DISABLED"] = "1"
        logging.info("MILVUS_LITE_DISABLED not set; defaulting to 1 on Windows to skip embedded Milvus Lite")

    if args.mode == "batch":
        input_root = Path(args.input_root) if args.input_root else DEFAULT_INPUT_ROOT
        output_root = Path(args.output_root) if args.output_root else DEFAULT_OUTPUT_ROOT
        logging.info(
            "Running batch mode: parser=%s input=%s output=%s",
            args.parser_addr,
            input_root,
            output_root,
        )
        run_batch(parser_addr=args.parser_addr, input_root=input_root, output_root=output_root)
        return

    if args.mode == "grpc":
        env_addr = os.environ.get("PY_INFERENCE_ADDR")
        env_host, env_port = _parse_addr_env(env_addr) if env_addr else (None, 0)
        host = env_host if env_host and env_host != "inference-py" else args.host
        port = env_port if env_port and env_port > 0 else args.port
        logging.info("Starting inference-py gRPC server on %s:%s", host, port)
        grpc_server.serve(host=host, port=port)
        return

    # 默认: FastAPI 快速 AI 审查模式
    app = create_app(parser_addr=args.parser_addr)
    logging.info("Starting FastAPI server on %s:%s (parser %s)", args.host, args.port, args.parser_addr)
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
