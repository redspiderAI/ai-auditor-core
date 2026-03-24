# Java 分支（engine-java）提交前审查报告

**审查日期**：2026年3月24日
**审查对象**：`engine-java` 模块
**审查人**：Manus AI

本报告对 `engine-java` 分支进行了全面、严谨的审查，涵盖了需求符合度、代码设计规范、Proto 协议一致性以及 gRPC 通信协议合规性，并在沙盒环境中进行了实际的构建与测试验证。

---

## 1. 沙盒测试与需求清单符合度验证

在沙盒环境中，我们配置了 Java 17 和 Maven 3.9.6 环境，对项目进行了编译和单元测试。

### 1.1 构建与运行状态
* **编译状态**：**通过**。项目能够成功编译，没有语法错误。
* **测试状态**：**失败**。运行 `mvn test` 时，27 个测试中有 7 个失败。

### 1.2 需求清单符合度分析

| 需求项 | 预期要求 | 实际实现情况 | 结论 |
| :--- | :--- | :--- | :--- |
| **核心语言** | Java 21（利用虚拟线程） | `pom.xml` 中配置为 `<java.version>17</java.version>`，`GrpcServer` 中虚拟线程代码被注释。 | ❌ **不符合** |
| **框架** | Spring Boot 3.x + Spring Batch | 使用了 Spring Boot 3.2.0，但**未引入 Spring Batch 依赖**，未实现大文件分批处理。 | ❌ **不符合** |
| **规则引擎** | Drools 8.0+ | 引入了 Drools 8.44.0.Final，实现了业务逻辑解耦。 | ✅ **符合** |
| **数据库/缓存** | Redis + PostgreSQL | `pom.xml` 中**未引入**任何 Redis 或 PostgreSQL 相关的依赖。 | ❌ **不符合** |
| **通讯** | gRPC (Stubby) | 实现了 gRPC 服务端，配置了 `protobuf-maven-plugin`。 | ✅ **符合** |
| **模块一：排版规则** | 校验标题序列、视觉参数、图表逻辑 | 实现了 `formatting.drl`，包含行距、层级、图表跨页等规则。 | ✅ **符合** |
| **模块二：引用校验** | 双向追溯、排序校验、格式完整性 | 实现了 `reference.drl` 和位图优化算法（`BitmapReferenceOptimizer`）。 | ✅ **符合** |
| **模块三：完整性** | 必备章节、语义触发 | 实现了 `integrity.drl` 和 `DocumentStateMachine`。 | ✅ **符合** |

**测试失败原因深度剖析**：
1. **Drools 规则语法错误**：测试用的 `formatting.drl` 中使用了 `$section.getId()`、`$section.getFontFamily()` 等方法，但 `Section` 类（由 protobuf 生成）中只有 `getSectionId()` 和 `getPropsMap()`，导致 Drools 编译失败（`The method getId() is undefined for the type Section`）。
2. **全局变量未定义**：`DocumentAuditorServiceImpl` 中尝试 `session.setGlobal("results", results)`，但 `reference.drl` 中全局变量定义存在问题，导致 `Unexpected global [results]` 异常。
3. **测试断言失败**：由于 Drools 引擎初始化失败或规则未触发，导致 `GB7714AccuracyTest`、`FormattingCoverageTest` 等测试未能检出预期数量的问题。

---

## 2. 代码设计规范检查

根据《redspiderAI 项目代码设计规范 (v1.0)》，我们对代码进行了静态分析。

### 2.1 文件规模控制 (File Size Limit)
* **硬性指标**：单文件不超过 1,000 行。
* **检查结果**：**通过**。最长的文件为 `IntegrityScanner.java`（303 行），远低于 1,000 行的限制。

### 2.2 函数精简原则 (Function Granularity)
* **硬性指标**：单函数不超过 100 行。
* **检查结果**：**通过**。经过脚本扫描，未发现超过 100 行的超长函数。

### 2.3 消灭冗余代码 (DRY)
* **准则**：严禁声明功能近似或完全一致的函数。
* **检查结果**：**存在问题**。
  * `GrpcServer.java` 和 `EmbeddedGrpcServer.java` 存在高度重合的逻辑。两者都试图启动 gRPC 服务，且 `EmbeddedGrpcServer` 包含了大量硬编码的 RPC 方法注册逻辑，显得冗余。
  * `DocumentAuditorServiceImpl.java` 和 `EmbeddedGrpcServer.java` 中都存在分数计算逻辑（`calculateTotalScore` vs `calculateScoreImpact`），且两者的权重计算方式不一致，违反了 DRY 原则。

### 2.4 目录结构规范 (Directory Organization)
* **硬性指标**：每个文件夹中的代码文件数量不得超过 6 个。
* **检查结果**：**通过**。各包下的文件数量均控制在合理范围内（如 `checkers` 包 4 个，`validators` 包 4 个）。

---

## 3. Proto 文件一致性验证

对比了 `shared/protos/auditor.proto` 和 `services/engine-java/src/main/protobuf/auditor.proto`。

* **检查结果**：**完全一致**。
* 两个文件在消息结构（`ParseRequest`, `ParsedData`, `AuditRequest`, `AuditResponse`, `Issue` 等）和字段编号上完全相同，符合 Schema-First 流程的要求。

---

## 4. gRPC 通信协议合规性分析

### 4.1 接口实现合规性
* `DocumentAuditorServiceImpl` 正确继承了 `DocumentAuditorGrpc.DocumentAuditorImplBase`。
* 正确重写了 `auditRules` 方法，输入参数为 `AuditRequest`，输出通过 `StreamObserver<AuditResponse>` 返回，符合 gRPC 异步流式处理规范。

### 4.2 架构与启动合规性（存在严重隐患）
* **双服务冲突**：项目中同时存在 `GrpcServer`（监听 9191 端口）和 `EmbeddedGrpcServer`（监听 9192 端口）。`GrpcServer` 使用了 `@PostConstruct` 自动启动，而 `EmbeddedGrpcServer` 似乎是遗留代码。
* **未实现的方法**：`ParseDocument`、`AnalyzeSemantics` 和 `InjectAnnotations` 在 Java 端不需要实现（应由 Rust 和 Python 模块处理），但 `EmbeddedGrpcServer` 中却包含了这些方法的占位实现，这在微服务架构中是不合理的。Java 模块只应暴露 `AuditRules` 服务。

---

## 5. 修复建议清单 (Action Items)

为了使分支达到可合并的工业级标准，成员 B 必须完成以下修复：

1. **修复 Drools 测试规则语法**：
   * 修改 `src/test/resources/rules/formatting/formatting.drl`，将 `$section.getId()` 替换为 `$section.getSectionId()`。
   * 将 `$section.getFontFamily()` 等属性访问替换为从 `props` Map 中获取，例如 `(String)$section.getPropsMap().get("font-family")`。
2. **统一全局变量定义**：
   * 确保所有 `.drl` 文件（包括测试文件）中正确声明 `global java.util.List results;`，以解决 `Unexpected global [results]` 错误。
3. **升级 Java 版本与引入缺失依赖**：
   * 将 `pom.xml` 中的 `<java.version>17</java.version>` 修改为 `21`。
   * 引入需求清单中明确要求的 `spring-boot-starter-batch`、`spring-boot-starter-data-redis` 和 `postgresql` 依赖。
4. **清理冗余的 gRPC 启动器**：
   * 删除 `EmbeddedGrpcServer.java`，统一使用 `GrpcServer.java`。
   * 统一分数计算逻辑，删除重复的 `calculateScoreImpact` 方法。
5. **启用虚拟线程**：
   * 在升级到 Java 21 后，取消 `GrpcServer.java` 中 `Executors.newVirtualThreadPerTaskExecutor()` 的注释，以满足高并发处理需求。
