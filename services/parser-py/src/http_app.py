import asyncio
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from src.core.parser import DocxParser, DocumentSection, ElementType
from src.core.pdf_parser import PdfParser


# 临时上传目录，避免占用仓库空间
UPLOAD_ROOT = Path(tempfile.gettempdir()) / "parser_py_uploads"


def _ensure_upload_root() -> Path:
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)
    return UPLOAD_ROOT


def _save_upload(file: UploadFile) -> Path:
    target = _ensure_upload_root() / file.filename
    with target.open("wb") as fh:
        fh.write(file.file.read())
    return target


def _section_to_dict(section) -> dict:
    if isinstance(section, DocumentSection):
        element = section.element_type
        if hasattr(element, "level"):
            element_type = "heading"
            level = getattr(element, "level", 0)
        elif element == ElementType.Table:
            element_type = "table"
            level = 0
        elif element == ElementType.Equation:
            element_type = "equation"
            level = 0
        else:
            element_type = "paragraph"
            level = 0

        return {
            "id": section.id,
            "element_type": element_type,
            "level": level,
            "raw_text": section.raw_text,
            "formatting": section.formatting,
            "xml_path": section.xml_path,
            "offset": section.offset,
        }

    # 已是 dict（PDF 解析路径）
    return section


def _build_payload(result: dict, source_path: Path) -> dict:
    normalized_name = str(source_path).replace("/", "_").replace("\\", "_")
    doc_id = f"doc_{normalized_name}"
    return {
        "doc_id": doc_id,
        "source": str(source_path),
        "sections": [_section_to_dict(s) for s in result.get("sections", [])],
        "positions": result.get("positions", {}),
        "metadata": result.get("metadata", {}),
    }


def _parse_file(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"file not found: {path}")

    suffix = path.suffix.lower()
    if suffix == ".docx":
        parser = DocxParser()
    elif suffix == ".pdf":
        parser = PdfParser()
    else:
        raise ValueError("仅支持 .docx 或 .pdf")

    result = parser.parse(path)
    return _build_payload(result, path)


def create_app() -> FastAPI:
    app = FastAPI(title="Parser PY API")

    @app.post("/parse")
    async def parse(file: UploadFile = File(...), file_path: str | None = None):
        try:
            if file_path:
                path = Path(file_path)
                if not path.is_file():
                    raise HTTPException(status_code=400, detail="file_path 不存在")
            else:
                if file.content_type not in {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/pdf",
                    "application/octet-stream",
                    None,
                }:
                    raise HTTPException(status_code=400, detail="仅支持 .docx 或 .pdf 上传")
                path = _save_upload(file)

            payload = await asyncio.to_thread(_parse_file, path)
            return JSONResponse(payload)
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=500, detail=f"解析失败: {exc}")

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app
