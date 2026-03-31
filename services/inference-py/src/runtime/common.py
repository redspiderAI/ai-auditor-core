import logging
import os
import re
import tempfile
import zipfile
from pathlib import Path
from typing import Tuple

from src.protos import auditor_pb2

# runtime paths
REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_INPUT_ROOT = REPO_ROOT / "data" / "input"
DEFAULT_OUTPUT_ROOT = REPO_ROOT / "data" / "output"
DEFAULT_PARSER_HTTP = os.environ.get("PARSER_HTTP_URL") or os.environ.get("PARSER_HTTP_ENDPOINT")
UPLOAD_ROOT = Path(tempfile.gettempdir()) / "ai_auditor_uploads"
TARGET_PATTERN = re.compile(r"毕业论文\.docx$", re.IGNORECASE)
# Full audit message now indicates all checks are enabled
EMERGENCY_MESSAGE = "已启用标点规范、纠错、学术风格与参考文献真实性校验。"


def parse_addr_env(env_value: str) -> Tuple[str, int]:
    """解析类似 'inference-py:8123' 或 '0.0.0.0:8123' 或 '8123' 的地址，返回 (host, port)."""
    default_host = "127.0.0.1"
    if not env_value:
        return default_host, 0
    parts = env_value.rsplit(":", 1)
    if len(parts) == 1:
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
    return host_part or default_host, port


def map_parser_http_payload_to_parsed(payload: dict, doc_path: Path) -> auditor_pb2.ParsedData:
    parsed = auditor_pb2.ParsedData()
    parsed.doc_id = payload.get("doc_id") or f"doc_{doc_path}"

    metadata = payload.get("metadata") or {}
    parsed.metadata.title = str(metadata.get("title")) if metadata.get("title") is not None else ""
    parsed.metadata.page_count = int(metadata.get("total_pages", metadata.get("page_count", 0)) or 0)
    parsed.metadata.margin_top = float(metadata.get("margin_top", 0.0) or 0.0)
    parsed.metadata.margin_bottom = float(metadata.get("margin_bottom", 0.0) or 0.0)

    positions = payload.get("positions") or {}

    for section in payload.get("sections", []):
        sec = parsed.sections.add()
        # 注意：不能用 `or` 判断，因为 id=0 会被视为 False 导致错误地使用默认值。
        # 优先使用明确提供的 'id' 或 'section_id'（即使为 0），否则使用当前已添加节数 + 1 作为默认值。
        sec_id_raw = section.get("id") if "id" in section else section.get("section_id")
        if sec_id_raw is None:
            sec.section_id = len(parsed.sections) + 1
        else:
            try:
                sec.section_id = int(sec_id_raw)
            except Exception:
                sec.section_id = len(parsed.sections) + 1
        element_type = (section.get("element_type") or section.get("type") or "paragraph").lower()
        if element_type.startswith("head"):
            sec.type = "heading"
        elif element_type.startswith("table"):
            sec.type = "table"
        elif element_type.startswith("equation") or element_type.startswith("math"):
            sec.type = "equation"
        else:
            sec.type = "paragraph"
        sec.level = int(section.get("level", 0) or 0)
        sec.text = section.get("raw_text") or section.get("text") or ""

        fmt = section.get("formatting") or section.get("props") or {}
        for k, v in fmt.items():
            sec.props[k] = str(v)

        if section.get("xml_path"):
            sec.props["xml_path"] = str(section.get("xml_path"))
        if section.get("offset") is not None:
            sec.props["offset"] = str(section.get("offset"))

        pos = positions.get(str(sec.section_id)) or positions.get(sec.section_id)
        if isinstance(pos, dict):
            for pk, pv in pos.items():
                sec.props[f"position.{pk}"] = str(pv)

    return parsed


def parse_via_http(parser_http_url: str, doc_path: Path) -> auditor_pb2.ParsedData | None:
    import httpx

    if not parser_http_url:
        return None

    try:
        with open(doc_path, "rb") as fh:
            files = {"file": (doc_path.name, fh, "application/octet-stream")}
            resp = httpx.post(parser_http_url, files=files, timeout=120)
        resp.raise_for_status()
        payload = resp.json()
        return map_parser_http_payload_to_parsed(payload, doc_path)
    except Exception as exc:  # pragma: no cover - network/runtime failure
        logging.warning("parser HTTP fallback failed for %s via %s: %s", doc_path, parser_http_url, exc)
        return None


def fallback_parsed(doc_path: Path) -> auditor_pb2.ParsedData:
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


def extract_docx_text(doc_path: Path) -> str:
    if not doc_path.suffix.lower() == ".docx":
        return ""
    try:
        with zipfile.ZipFile(doc_path) as zf:
            with zf.open("word/document.xml") as f:
                xml_bytes = f.read()
        text = re.sub(r"<[^>]+>", "", xml_bytes.decode("utf-8", errors="ignore"))
        return text
    except Exception as exc:  # pragma: no cover - fallback only
        logging.warning("docx extraction failed for %s: %s", doc_path, exc)
        return ""


def extract_references_from_text(text: str) -> list[str]:
    refs: set[str] = set()
    line_pattern = re.compile(r"\[\d+\].+")
    for line in text.splitlines():
        match = line_pattern.search(line)
        if match:
            candidate = match.group(0).strip()
            if candidate:
                refs.add(candidate)
    return list(refs)


__all__ = [
    "REPO_ROOT",
    "DEFAULT_INPUT_ROOT",
    "DEFAULT_OUTPUT_ROOT",
    "DEFAULT_PARSER_HTTP",
    "UPLOAD_ROOT",
    "TARGET_PATTERN",
    "EMERGENCY_MESSAGE",
    "parse_addr_env",
    "map_parser_http_payload_to_parsed",
    "parse_via_http",
    "fallback_parsed",
    "extract_docx_text",
    "extract_references_from_text",
]
