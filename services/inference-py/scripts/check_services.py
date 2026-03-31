#!/usr/bin/env python3
"""检查服务连通性：Milvus、DashScope(Qwen)和 sentence-transformers。
输出 JSON 至 `scripts/check_services_output.json` 并打印到 stdout。
"""
from __future__ import annotations

import json
import os
import logging
import time
import concurrent.futures
from pathlib import Path

try:
    from dotenv import load_dotenv
except Exception:
    load_dotenv = None

HERE = Path(__file__).resolve().parent
ENV = HERE.parent / ".env"
if load_dotenv and ENV.exists():
    load_dotenv(ENV)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def check_milvus(host: str = None, port: int = None, timeout_s: int = 5) -> dict:
    host = host or os.environ.get("MILVUS_HOST", "127.0.0.1")
    port = int(port or os.environ.get("MILVUS_PORT", "19530"))
    res = {"available": False, "error": None, "host": host, "port": port}
    try:
        from pymilvus import connections, utility
    except Exception as e:
        res["error"] = f"pymilvus import error: {e}"
        return res

    try:
        # 尝试连接并快速检测
        connections.connect(host=host, port=port)
        res["available"] = True
        try:
            res["has_collections"] = utility.list_collections()
        except Exception as e:
            res["has_collections_error"] = str(e)
    except Exception as e:
        res["error"] = str(e)

    return res


def check_embedder() -> dict:
    res = {"available": False, "error": None, "model": "all-MiniLM-L6-v2"}
    try:
        from sentence_transformers import SentenceTransformer
    except Exception as e:
        res["error"] = f"sentence-transformers import error: {e}"
        return res

    try:
        model = SentenceTransformer(res["model"])
        # fast encode a short text
        vec = model.encode(["test"], normalize_embeddings=True)
        res["available"] = True
        res["vec_len"] = len(vec[0]) if vec is not None and len(vec) > 0 else None
    except Exception as e:
        res["error"] = str(e)

    return res


def check_dashscope(timeout_s: int = 10) -> dict:
    res = {"available": False, "error": None, "api_key_present": False}
    api_key = os.environ.get("DASHSCOPE_API_KEY")
    if not api_key:
        res["error"] = "DASHSCOPE_API_KEY not set"
        return res
    res["api_key_present"] = True

    try:
        import dashscope
    except Exception as e:
        res["error"] = f"dashscope import error: {e}"
        return res

    try:
        dashscope.api_key = api_key
        prompt = "请返回一段简单 JSON：{\"ok\":true}"  # 简短提示，期望结构化响应

        def call():
            return dashscope.Generation.call(model="qwen-long", prompt=prompt, top_p=1.0, temperature=0.0, max_tokens=50)

        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as exe:
            fut = exe.submit(call)
            try:
                resp = fut.result(timeout=timeout_s)
            except concurrent.futures.TimeoutError:
                fut.cancel()
                res["error"] = "dashscope call timeout"
                return res

        # Basic check of response
        status = getattr(resp, "status_code", None)
        res["status_code"] = status
        out = getattr(resp, "output", None)
        text = getattr(out, "text", None) if out is not None else None
        res["raw_text"] = text
        res["available"] = status == 200 and bool(text)
        if not res["available"] and status is not None:
            res["error"] = f"status_{status}"
    except Exception as e:
        res["error"] = str(e)

    return res


def main():
    out = {"timestamp": time.time()}
    out["milvus"] = check_milvus()
    out["embedder"] = check_embedder()
    out["dashscope"] = check_dashscope()

    out_path = HERE / "check_services_output.json"
    with out_path.open("w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
