# Go 模块深度分析与开发路线图

## 1. 现状深度剖析：代码与文档的“断层”

通过对 `i-auditor.zip` 源码与 `pasted_content.txt`（README/需求文档）的深度对比，发现当前项目处于 **“骨架已成，血肉未填”** 的初级阶段。

### 1.1 核心组件实现现状

| 组件名称 | 文档宣称功能 | 实际代码实现 | 状态 |
| :--- | :--- | :--- | :--- |
| **Orchestrator** | 分布式编排、并行审计、Temporal/Argo | 自定义工作流编排器，支持并行处理、重试、熔断 | ✅ 基础功能已实现 |
| **Async Manager** | Redis Streams、任务状态机 | Redis Streams + 内存队列回退，支持消费者组 | ✅ 已实现 |
| **Monitoring** | Prometheus 指标收集 | 完整的Prometheus指标端点和自定义指标 | ✅ 已实现 |
| **Report Center** | PDF 生成、Word 批注、ZIP 打包 | 仅生成 `report.json`，ZIP 也是 JSON 的重命名占位。 | ❌ 核心功能缺失 |
| **TempManager** | 自动清理、生命周期管理 | 目录不存在，代码中仅有 `os.MkdirAll`，无清理逻辑。 | ❌ 完全缺失 |
| **Circuit Breaker** | 熔断保护、容错设计 | 已在工作流中实现熔断器 | ✅ 已实现 |
| **Task Store** | 任务状态持久化 | 内存存储，无法持久化 | ⚠️ 无法持久化 |

### 1.2 关键技术阻塞点

1.  **gRPC 协议不一致**：`auditor.proto` 定义的 `go_package` 路径与当前项目路径不匹配，导致 `make proto` 生成的代码无法直接被 `worker_grpc.go` 引用。
2.  **并行逻辑缺失**：需求要求并行调用 Java 和 Python 模块，但 `worker_grpc.go` 中是顺序执行的。
3.  **结果聚合不完整**：仅实现了去重，未实现按 `section_id` 排序，这会影响最终报告的阅读体验。
4.  **基础设施依赖真空**：README 提到的 Redis Streams、Prometheus、Argo 等在代码中均无任何初始化或调用逻辑。
5.  **任务状态持久化**：任务状态仅在内存中存储，服务重启后会丢失。

---

## 2. 接下来做什么：分阶段开发路线图

为了将系统从“演示模式”推向“生产模式”，建议按以下三个阶段进行迭代：

### 第一阶段：打通全链路（集成与通信）
*   **修正 Proto 契约**：统一 `go_package` 路径，确保 `protoc` 生成的代码能正确导入。
*   **激活 gRPC 调用**：取消 `worker_grpc.go` 中的注释，实现真实的 RPC 调用，并处理连接池与超时。
*   **实现并行审计**：使用 `sync.WaitGroup` 或 `errgroup` 并行触发 Java 和 Python 审查，缩短整体耗时。
*   **完善结果聚合**：引入 `sort` 包，确保 Issue 列表按文档段落顺序排列。

### 第二阶段：强化交付能力（报告与存储）
*   **实现 PDF 生成**：激活 `gofpdf` 逻辑，设计报告模板（包含合规率仪表盘、错误分类统计图）。
*   **实现 ZIP 打包**：使用 `archive/zip` 真正将 PDF 报告和（由 Rust 返回的）带批注 Word 打包。
*   **引入 Redis 存储**：将内存 `store` 替换为 Redis，支持任务状态的持久化和跨实例共享。
*   **建立 TempManager**：实现定时清理任务（Cron），自动删除超过 24 小时的临时文件。

### 第三阶段：生产级增强（容错与监控）
*   **集成熔断器**：在调用 Python LLM 服务时加入熔断逻辑，防止下游超时导致网关协程堆积。
*   **接入 Prometheus**：暴露 `/metrics` 接口，统计各阶段（解析、审计、生成）的耗时。
*   **工作流引擎迁移**：若任务复杂度进一步增加，将硬编码的 `process` 逻辑迁移至 `Temporal`，实现真正的分布式任务补偿。

---

## 3. 技术方案建议清单

| 维度 | 建议方案 | 推荐库/工具 |
| :--- | :--- | :--- |
| **并发控制** | 使用 `golang.org/x/sync/errgroup` 处理并行 RPC | `errgroup` |
| **报告生成** | 结合 `html/template` 与 `wkhtmltopdf` 或继续完善 `gofpdf` | `gofpdf` / `weasyprint` |
| **熔断限流** | 引入 `github.com/sony/gobreaker` | `gobreaker` |
| **任务队列** | 使用 `Redis Streams` 或 `Asynq` | `asynq` |
| **文件清理** | 使用 `robfig/cron` 定时扫描 `temp_docs` | `cron` |
| **任务存储** | 使用 `Redis` 或 `PostgreSQL` 实现持久化存储 | `Redis` / `PostgreSQL` |

---

## 4. 总结

您现在的项目已经完成了 **API 契约定义** 和 **基础路由搭建**，但 **业务逻辑实现度约为 30%**。接下来的核心任务是 **“去 Mock 化”**，即通过真实的 gRPC 通信和物理文件处理，将四个微服务真正串联起来。
