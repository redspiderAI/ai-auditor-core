"""Offline/batch runner that pulls parsed data from parser-rs via gRPC
and writes inference results to data/output. Only processes files whose
name ends with "毕业论文.docx" under data/input.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import re
from pathlib import Path
from typing import List

import grpc

from src.grpc_server import DocumentAuditorServicer
from src.protos import auditor_pb2, auditor_pb2_grpc

# Only run inference on thesis documents, ignore everything else.
TARGET_PATTERN = re.compile(r"毕业论文\.docx$", re.IGNORECASE)


def _issue_to_dict(issue: auditor_pb2.Issue) -> dict:
    """Convert Issue proto into a JSON-serializable dict."""
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


def _discover_targets(input_root: Path) -> List[Path]:
    """Find .docx files whose filename ends with '毕业论文.docx'."""
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


__all__ = ["run_batch"]


def _default_paths():
    # __file__ = services/inference-py/src/batch_runner.py -> parents[3] == repo root
    repo_root = Path(__file__).resolve().parents[3]
    return repo_root / "data" / "input", repo_root / "data" / "output"


def _cli():
    parser = argparse.ArgumentParser()
    parser.add_argument("--parser-addr", default=os.environ.get("PARSER_GRPC_ADDR") or "127.0.0.1:52051")
    parser.add_argument("--input-root", default=None, help="Override input root (default repo data/input)")
    parser.add_argument("--output-root", default=None, help="Override output root (default repo data/output)")
    args = parser.parse_args()

    input_root_default, output_root_default = _default_paths()
    input_root = Path(args.input_root) if args.input_root else input_root_default
    output_root = Path(args.output_root) if args.output_root else output_root_default

    logging.basicConfig(level=logging.INFO)
    run_batch(parser_addr=args.parser_addr, input_root=input_root, output_root=output_root)


if __name__ == "__main__":
    _cli()
