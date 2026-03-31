import argparse
import logging
import os

from src.runtime.batch_runtime import run_batch_mode
from src.runtime.common import DEFAULT_PARSER_HTTP, DEFAULT_INPUT_ROOT, DEFAULT_OUTPUT_ROOT
from src.runtime.fastapi_runner import run_fastapi
from src.runtime.grpc_runner import resolve_host_port, run_grpc


def run(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="AI Auditor entrypoint")
    parser.add_argument("--mode", choices=["fastapi", "batch", "grpc"], default="fastapi", help="运行模式：fastapi(默认)、batch、grpc")
    parser.add_argument("--host", default="0.0.0.0", help="FastAPI/GRPC bind host")
    parser.add_argument("--port", type=int, default=8000, help="FastAPI/GRPC bind port")
    parser.add_argument("--parser-addr", default=os.environ.get("PARSER_GRPC_ADDR") or "127.0.0.1:52051", help="parser-rs gRPC address host:port")
    parser.add_argument("--parser-http", default=DEFAULT_PARSER_HTTP, help="parser-py FastAPI endpoint (HTTP fallback)")
    parser.add_argument("--input-root", default=None, help=f"batch 模式输入目录 (默认 {DEFAULT_INPUT_ROOT})")
    parser.add_argument("--output-root", default=None, help=f"batch 模式输出目录 (默认 {DEFAULT_OUTPUT_ROOT})")
    args = parser.parse_args(args=argv)

    logging.basicConfig(level=logging.INFO)

    if os.name == "nt" and not os.environ.get("MILVUS_LITE_DISABLED"):
        os.environ["MILVUS_LITE_DISABLED"] = "1"
        logging.info("MILVUS_LITE_DISABLED not set; defaulting to 1 on Windows to skip embedded Milvus Lite")

    if args.mode == "batch":
        run_batch_mode(parser_addr=args.parser_addr, input_root=args.input_root, output_root=args.output_root)
        return

    if args.mode == "grpc":
        host, port = resolve_host_port(default_host=args.host, default_port=args.port)
        run_grpc(host=host, port=port)
        return

    run_fastapi(host=args.host, port=args.port, parser_addr=args.parser_addr, parser_http_url=args.parser_http)


__all__ = ["run"]
