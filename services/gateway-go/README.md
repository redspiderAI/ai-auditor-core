# Gateway-Go

分布式调度网关与交付中心，作为AI审计系统的核心组件，负责协调各个微服务模块的工作流。

## 功能特性

- 文件上传与异步任务处理
- 任务状态跟踪与管理
- 实时WebSocket通知
- 自动临时文件管理与清理
- 完整的错误处理与恢复机制
- 分布式审查编排（串行解析 + 并行审计）
- 熔断器保护（防止下游服务故障扩散）
- 结构化审查报告（JSON + PDF格式）
- 数据可视化（问题分布图表）

## API接口

- `POST /api/v1/upload` - 上传文件并创建任务
- `GET /api/v1/tasks/:id` - 查询任务状态
- `GET /api/v1/report/:id` - 获取审查报告
- `GET /api/v1/download/:id` - 下载处理后的文档
- `GET /ws/task/:id` - WebSocket实时通知

## 环境变量

- `GATEWAY_PORT` - 网关服务端口 (默认: 8080)
- `TEMP_DIR` - 临时文件目录 (默认: ./temp_docs)
- `RUST_PARSER_ADDR` - Rust解析服务地址 (默认: parser-rs:52051)
- `JAVA_ENGINE_ADDR` - Java引擎服务地址 (默认: engine-java:9191)
- `PY_INFERENCE_ADDR` - Python推理服务地址 (默认: inference-py:50051)

## 快速启动

```bash
# 构建并运行（启用gRPC模式）
go build -tags grpc
./gateway-go

# 或使用Docker
docker build -t gateway-go .
docker run -p 8080:8080 \
  -e RUST_PARSER_ADDR=parser-rs:52051 \
  -e JAVA_ENGINE_ADDR=engine-java:9191 \
  -e PY_INFERENCE_ADDR=inference-py:50051 \
  gateway-go
```

## 测试

```bash
# 运行所有测试
go test ./...

# 运行特定包的测试
go test ./src/handlers/
go test ./src/store/
```

## 架构说明

本系统采用微服务架构，作为系统的"神经中枢"，负责：

1. 接收用户请求
2. 管理长耗时任务的工作流协同
3. 监控各微服务健康状态
4. 汇总各方数据生成结构化的审查报告
5. 提供实时状态更新
6. 实现分布式审查编排（先调用解析服务，再并行调用规则和语义服务）
7. 通过熔断器保护系统免受下游服务故障影响
8. 生成包含图表和统计数据的PDF报告

## 工作流编排

系统实现了一个智能的审查工作流：

1. **解析阶段**：调用Rust解析服务获取文档结构数据
2. **并行审计**：同时调用Java规则引擎和Python语义分析服务
3. **结果聚合**：合并两路审计结果，去重并按重要性排序
4. **报告生成**：生成JSON和PDF格式的详细审查报告

## 依赖

- Echo - Web框架
- Gorilla WebSocket - WebSocket支持
- Google UUID - 生成唯一标识符
- gRPC - 微服务间通信
- gofpdf - PDF报告生成