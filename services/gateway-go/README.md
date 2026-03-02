# Gateway-Go

分布式调度网关与交付中心，负责在AI审计系统内协调解析、审计、生成的全链路工作流。

## 功能特性

- 文件上传与异步任务处理
- 任务状态跟踪与管理（含WebSocket实时通知）
- 分布式审查编排：串行解析 + 并行审计 + 结果聚合
- 熔断保护、错误恢复与临时文件自动清理
- 结构化审查报告（JSON + PDF）与结果下载
- 数据可视化与审查进度反馈

## 系统架构与工作流

1. 接收用户请求并创建任务，状态写入存储
2. **解析阶段**：调用Rust解析服务获取文档结构
3. **并行审计**：同时调用Java规则引擎与Python语义分析
4. **结果聚合**：去重、排序后生成报告
5. **交付**：生成JSON/PDF、带注释文档并支持下载/回调/推送
6. 熔断器避免下游故障扩散，监控健康状态

### 项目目录

```
gateway-go/
├── main.go                 # 应用入口
├── go.mod / go.sum         # 依赖定义
├── Dockerfile              # Docker构建
├── README.md               # 项目说明（本文件）
├── API.md                  # API文档（已并入本文件）
├── .env.example            # 环境变量示例
├── Makefile                # 构建脚本
├── k8s-deployment.yaml     # Kubernetes部署
├── src/
│   ├── handlers/           # HTTP处理器
│   ├── store/              # 任务存储
│   ├── worker/             # 后台任务处理（本地 / gRPC）
│   ├── tempmanager/        # 临时文件管理
│   ├── notification/       # 通知服务
│   └── mock/               # 模拟依赖
└── temp_docs/              # 临时文件目录
```

### 核心组件

- Handlers：上传、状态查询、报告获取、下载、健康检查
- Store：任务创建/查询/更新，状态机与报告写入
- Worker：本地模拟或gRPC模式完成解析/审计/生成
- TempManager：临时文件生命周期与清理
- Notification：WebSocket推送与客户端管理

## API 接口

- 提交审核任务：POST `/api/v1/audit`
  - `multipart/form-data`，字段 `file`（支持 .docx / .pdf / .txt 等）
- 查询任务状态：GET `/api/v1/tasks/{id}`
- 获取审查报告：GET `/api/v1/report/{id}`
- 下载结果包：GET `/api/v1/download/{id}`（ZIP，含带注释文档与报告）
- WebSocket 通知：GET `/ws/task/{id}`
- 健康检查：GET `/health`

任务状态：Pending | Queued | Parsing | Auditing | Generating | Completed | Error。

## 环境与配置

- `GATEWAY_PORT`：服务端口（默认 8080）
- `TEMP_DIR`：临时文件目录（默认 ./temp_docs）
- `RUST_PARSER_ADDR`：Rust 解析服务地址（默认 parser-rs:52051）
- `JAVA_ENGINE_ADDR`：Java 引擎服务地址（默认 engine-java:9191）
- `PY_INFERENCE_ADDR`：Python 推理服务地址（默认 inference-py:50051）
- 构建标签：`grpc` 启用 gRPC worker（生产）；无标签使用本地模拟（开发）
- VS Code 可设置 `go.buildTags: "grpc"` 与 `GOFLAGS="-tags=grpc"`（或终端设置环境变量）

## 构建与运行

### 本地

```bash
# 开发（本地worker）
go build
./gateway-go

# 生产（gRPC worker）
go build -tags grpc
./gateway-go
```

### Docker

```bash
docker build -t gateway-go .
docker run -p 8080:8080 \
  -e RUST_PARSER_ADDR=parser-rs:52051 \
  -e JAVA_ENGINE_ADDR=engine-java:9191 \
  -e PY_INFERENCE_ADDR=inference-py:50051 \
  gateway-go
```

### Makefile

```bash
make help   # 查看命令
make build  # 构建
make run    # 运行
make test   # 测试
```

## 测试与结果解读

### 运行测试

```bash
go test ./...
go test ./src/handlers/
go test ./src/store/
go test -v ./...        # 详细输出
```

集成测试示例（PowerShell）：`./test.ps1`。

### 如何查看和理解测试结果

- 查询任务返回：`id`、`status`、`progress`、源/注释/报告路径、时间戳
- 报告结构：`document_info`（标题/页数/大小）、`issues`（code/message/section/severity/suggestion/snippet）、`issue_summary`、`compliance_rate`、`total_score`
- 合规率解读：100% 完全合规；90-99% 高度合规；80-89% 基本合规；70-79% 一般；<70% 需大量修正
- 严重程度：CRITICAL/HIGH/MEDIUM/LOW/INFO；类别：CITATION/FORMATTING/SEMANTIC/STRUCTURE/STYLE
- 成功校验清单：
  - 任务状态变为 Completed
  - 返回有效 JSON 报告，合规率合理（通常 >70%）
  - 生成带注释文档与 PDF 报告
  - 响应时间通常 <60s

## 部署

- Docker：使用本仓库 Dockerfile 直接构建
- Kubernetes：参考 k8s-deployment.yaml 部署

## 最佳实践与故障排除

- 错误处理：检查并记录所有错误；保持资源关闭
- 并发安全：保护共享资源，避免 goroutine 泄漏
- 日志与调试：`go run -race`、pprof、日志、dlv
- 常见问题：
  - 端口占用：调整 `GATEWAY_PORT`
  - 临时文件堆积：检查 TempManager 清理
  - 网络/依赖故障：确认下游服务可用、网络连通
  - 构建标签未生效：校验 `GOFLAGS` 或 VS Code 设置

## 依赖

- Echo（HTTP）
- Gorilla WebSocket
- gRPC
- Google UUID
- gofpdf（PDF 生成）
