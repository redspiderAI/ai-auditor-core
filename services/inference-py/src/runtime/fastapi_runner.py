import asyncio
import os
import logging
import time
from pathlib import Path

import uvicorn
from fastapi import Body, FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from src.grpc_server import DocumentAuditorServicer
from src.fact_checking import ReferenceFactChecker
from src.semantic_detection import ComprehensiveSemanticDetector
from src.protos import auditor_pb2
from src.runtime.common import (
    DEFAULT_PARSER_HTTP,
    EMERGENCY_MESSAGE,
    UPLOAD_ROOT,
    extract_docx_text,
    extract_references_from_text,
    fallback_parsed,
    map_parser_http_payload_to_parsed,
    parse_via_http,
)


def _compute_score_impact(issues: list[auditor_pb2.Issue]) -> float:
    high = sum(1 for issue in issues if issue.severity == auditor_pb2.HIGH)
    medium = sum(1 for issue in issues if issue.severity == auditor_pb2.MEDIUM)
    low = sum(1 for issue in issues if issue.severity == auditor_pb2.LOW)
    total_weight = high * 0.7 + medium * 0.3 + low * 0.1
    return min(total_weight / max(len(issues), 1), 1.0) if issues else 0.0


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
    mode: str = "FULL",
) -> dict:
    return {
        "total_issues": len(resp.issues),
        "score": resp.score_impact,
        "mode": mode,
        "message": EMERGENCY_MESSAGE,
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


def _ensure_upload_root() -> Path:
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)
    return UPLOAD_ROOT


def _save_upload(file: UploadFile) -> Path:
    temp_dir = _ensure_upload_root()
    target = temp_dir / file.filename
    with target.open("wb") as f:
        content = file.file.read()
        f.write(content)
    return target


async def _build_resp_from_text(
    text: str, references: list[str], fact_checker: ReferenceFactChecker | None
) -> auditor_pb2.AuditResponse:
    detector = ComprehensiveSemanticDetector()

    # 并行化：将语义检测（CPU/本地耗时）放到线程池执行，同时异步并发执行参考文献校验（可能是网络/LLM/向量检索）
    detect_task = asyncio.to_thread(detector.detect_issues, text)
    fact_task = None
    if references and fact_checker:
        fact_task = asyncio.create_task(fact_checker.check_references(references))

    issues = await detect_task
    resp = auditor_pb2.AuditResponse()
    resp.issues.extend(issues)

    if fact_task is not None:
        # 为整个参考文献校验设置一个总超时，防止大量参考或外部调用将单个请求阻塞很长时间
        total_timeout = int(os.environ.get("FACT_CHECK_TOTAL_TIMEOUT_S", "30"))
        try:
            ref_results = await asyncio.wait_for(fact_task, timeout=total_timeout)
            for result in ref_results:
                if not result.is_valid:
                    resp.issues.extend(result.issues)
        except asyncio.TimeoutError:
            logging.warning(
                "Reference fact check total timeout after %s seconds; continuing without full fact-check",
                total_timeout,
            )

    resp.score_impact = _compute_score_impact(list(resp.issues))
    return resp


async def _audit_single_async(
    servicer: DocumentAuditorServicer, parser_addr: str, doc_path: Path, parser_http_url: str | None = DEFAULT_PARSER_HTTP
) -> tuple[auditor_pb2.ParsedData, auditor_pb2.AuditResponse, dict]:
    started_at = time.time()
    text_chars = 0
    references_found = 0
    parse_source = "grpc"
    try:
        import grpc

        with grpc.insecure_channel(parser_addr) as channel:
            from src.protos import auditor_pb2_grpc

            stub = auditor_pb2_grpc.DocumentAuditorStub(channel)
            parsed = stub.ParseDocument(
                auditor_pb2.ParseRequest(file_path=str(doc_path), template_type="")
            )
    except Exception as exc:
        parsed = parse_via_http(parser_http_url, doc_path)
        if parsed:
            parse_source = "http"
            logging.warning("parser gRPC unavailable at %s: %s; used HTTP fallback %s", parser_addr, exc, parser_http_url)
        else:
            parse_source = "fallback"
            parsed = fallback_parsed(doc_path)
            logging.warning("parser gRPC unavailable at %s: %s; using fallback parsed data", parser_addr, exc)

    if parse_source == "fallback":
        text = extract_docx_text(doc_path)
        references = extract_references_from_text(text) if text else []
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
        if not parsed.references:
            text_for_refs = extract_docx_text(doc_path)
            extracted_refs = extract_references_from_text(text_for_refs)
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


def create_app(parser_addr: str, parser_http_url: str | None = DEFAULT_PARSER_HTTP) -> FastAPI:
    app = FastAPI(title="AI Auditor (FastAPI)")

    servicer = DocumentAuditorServicer()

    @app.on_event("startup")
    async def _preheat_fact_checker():
        """服务启动时预热 ReferenceFactChecker（Milvus + 嵌入器），但带超时以避免阻塞启动。"""
        try:
            fact_checker = getattr(servicer, "fact_checker", None)
            if fact_checker:
                timeout_s = int(os.environ.get("FACT_CHECK_PREHEAT_TIMEOUT_S", "10"))
                try:
                    # 在后台线程执行同步预热，并为整个操作设置超时
                    await asyncio.wait_for(asyncio.to_thread(fact_checker.preheat), timeout=timeout_s)
                    logging.info("ReferenceFactChecker preheated")
                except asyncio.TimeoutError:
                    logging.warning("ReferenceFactChecker preheat timed out after %s seconds; continuing without full preheat", timeout_s)
        except Exception as e:
            logging.warning("ReferenceFactChecker preheat failed: %s", e)

    @app.post("/audit")
    async def audit(file: UploadFile = File(...), file_path: str | None = None):
        try:
            if file_path:
                path = Path(file_path)
                if not path.is_file():
                    raise HTTPException(status_code=400, detail="file_path 不存在")
            else:
                if file.content_type not in {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", None}:
                    raise HTTPException(status_code=400, detail="仅支持 .docx 上传")
                path = _save_upload(file)

            parsed, resp, summary = await _audit_single_async(servicer, parser_addr, path, parser_http_url)
            payload = _to_payload(parsed, resp, path, summary)
            return JSONResponse(payload)
        except HTTPException:
            raise
        except Exception as exc:  # pragma: no cover - runtime failure
            logging.exception("audit failed: %s", exc)
            raise HTTPException(status_code=500, detail=f"审查失败: {exc}")

    @app.post("/audit/parsed")
    async def audit_with_parsed(payload: dict = Body(...)):
        """直接接收 parser-py 解析后的 JSON（无需上传文件）。"""
        try:
            parsed = map_parser_http_payload_to_parsed(payload, doc_path=Path(payload.get("source", "doc")))

            def _run_audit():
                audit_req = auditor_pb2.AuditRequest()
                audit_req.data.CopyFrom(parsed)
                return servicer.AuditRules(audit_req, context=None)

            resp = await asyncio.to_thread(_run_audit)
            summary = _build_summary(
                resp,
                started_at=time.time(),
                ended_at=time.time(),
                text_chars=sum(len(s.text) for s in parsed.sections),
                references_found=len(parsed.references),
                mode="FULL",
            )
            source_path = Path(payload.get("source", payload.get("doc_id", "doc")))
            return JSONResponse(_to_payload(parsed, resp, source_path, summary))
        except HTTPException:
            raise
        except Exception as exc:  # pragma: no cover - runtime failure
            logging.exception("audit_with_parsed failed: %s", exc)
            raise HTTPException(status_code=500, detail=f"审查失败: {exc}")

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app


def run_fastapi(host: str, port: int, parser_addr: str, parser_http_url: str | None = DEFAULT_PARSER_HTTP) -> None:
    app = create_app(parser_addr=parser_addr, parser_http_url=parser_http_url)
    logging.info(
        "Starting FastAPI server on %s:%s (parser gRPC %s, http %s)",
        host,
        port,
        parser_addr,
        parser_http_url,
    )
    uvicorn.run(app, host=host, port=port)


__all__ = [
    "create_app",
    "run_fastapi",
]
