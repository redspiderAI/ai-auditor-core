import logging
import os

from src import grpc_server
from src.runtime.common import parse_addr_env


def run_grpc(host: str, port: int) -> None:
    logging.info("Starting inference-py gRPC server on %s:%s", host, port)
    grpc_server.serve(host=host, port=port)


def resolve_host_port(default_host: str, default_port: int) -> tuple[str, int]:
    env_addr = os.environ.get("PY_INFERENCE_ADDR")
    env_host, env_port = parse_addr_env(env_addr) if env_addr else (None, 0)
    host = env_host if env_host and env_host != "inference-py" else default_host
    port = env_port if env_port and env_port > 0 else default_port
    return host, port


__all__ = ["run_grpc", "resolve_host_port"]
