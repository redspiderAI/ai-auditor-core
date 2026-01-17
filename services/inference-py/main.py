import argparse
import logging
import os
from typing import Tuple

from src import grpc_server


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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1", help="gRPC bind host")
    parser.add_argument("--port", type=int, default=50051, help="gRPC bind port")
    args = parser.parse_args()

    # 优先读取环境变量 `PY_INFERENCE_ADDR`（格式示例: inference-py:8123 或 127.0.0.1:8123 或 8123）
    env_addr = os.environ.get("PY_INFERENCE_ADDR")
    env_host, env_port = _parse_addr_env(env_addr) if env_addr else (None, 0)

    host = env_host if env_host and env_host != "inference-py" else args.host
    port = env_port if env_port and env_port > 0 else args.port

    logging.basicConfig(level=logging.INFO)
    logging.info("Starting inference-py gRPC server on %s:%s", host, port)
    grpc_server.serve(host=host, port=port)


if __name__ == "__main__":
    main()
