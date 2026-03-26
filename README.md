# AI Auditor Core

跨语言文档审计平台的 mono-repo，包含 Rust/Java/Python/Go 四个服务，以及共享的 gRPC 协议定义，用于协同“解析-规则-推理-编排”全链路。

## 模块概览

- Rust 解析器（[services/parser-rs](services/parser-rs)）：高性能文档解析骨架，包含 `parser` / `layout` / `writer` / `grpc` 模块。
- Java 规则引擎（[services/engine-java](services/engine-java)）：Maven + Spring Boot，自动从 [shared/protos/auditor.proto](shared/protos/auditor.proto) 生成 protobuf POJO。
- Python 推理服务（[services/inference-py](services/inference-py)）：语义检测、事实核验、逻辑一致性等模块，使用 `uv` 管理环境。
- Go 网关（[services/gateway-go](services/gateway-go)）：HTTP 网关原型与本地 worker，计划对接其他三个服务的 gRPC 客户端。

## 当前进度

- Parser（Rust）：骨架可用，`cargo build` 可通过；核心解析/布局/写入与 gRPC 服务待实现。
- Engine（Java）：Maven 构建已接入 protoc 生成；Spring Boot 已搭建；下一步可补 gRPC stub。
- Inference（Python）：目录与测试脚本就绪（如 `tests/torch_test.py`）；待接入模型权重与服务化。
- Gateway（Go）：最小 HTTP 原型与 worker stub；需接入真实 gRPC 客户端连接 parser/engine/inference。
- Shared protos：统一协议位于 [shared/protos/auditor.proto](shared/protos/auditor.proto)。

## 快速开始（按模块）

前置依赖：Rust 工具链、Go 1.20+、JDK 17+ 与 Maven、Python 3.10+ 与 `uv`，可选 Docker（用于 compose）。

### Parser（Rust）

```bash
cd services/parser-rs
cargo build
```

### Engine（Java）

```bash
cd services/engine-java
mvn clean package
# 本地运行
mvn spring-boot:run
# 或运行打包后的 jar
java -jar target/engine-java-0.1.0-SNAPSHOT.jar
```

### Inference（Python）

```powershell
cd services/inference-py
uv venv
uv sync
uv run ./tests/torch_test.py
```

### Gateway（Go）

```bash
cd services/gateway-go
go build
./gateway
```

## Compose（WIP）

根目录的 [docker-compose.yml](docker-compose.yml) 用于后续多服务编排；待各服务暴露 gRPC/HTTP 端口后补充镜像名与端口映射。
