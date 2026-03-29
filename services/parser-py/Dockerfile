FROM python:3.11-slim

WORKDIR /app

# 安装 uv
RUN pip install --no-cache-dir uv

# 复制 pyproject.toml
COPY pyproject.toml .

# 安装依赖
RUN uv sync

# 复制源代码
COPY . .

# 生成 gRPC 代码
RUN uv run python -m grpc_tools.protoc -Ishared/protos --python_out=src --grpc_python_out=src shared/protos/auditor.proto

# 暴露 gRPC 端口
EXPOSE 50051

# 启动服务
CMD ["uv", "run", "python", "-m", "src.main", "grpc"]
