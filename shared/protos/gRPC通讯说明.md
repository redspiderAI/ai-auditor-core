# gRPC通讯协议

作为团队的“宪法”，`auditor.proto` 必须定义清晰的数据边界。以下是完整的文件内容以及一套成熟的工业级分发流程。

---

## 1. 如何分发给其他成员（Schema-First 流程）

为了确保四个人使用的接口永远同步，请遵循以下分发步骤：

### 第一步：成员 D 提交协议 (已完成)

成员 D 在本地创建分支 `feat/proto-init`，将上述文件提交到 `shared/protos/auditor.proto`，发起 PR 并合并到 `main`。

### 第二步：各成员同步并生成代码

所有成员执行 `git pull origin main`。随后，每个人在自己的目录下配置代码生成工具：

* **成员 A (Rust):**
在 `services/parser-rs/build.rs` 中添加：
```rust
fn main() {
    tonic_build::configure()
        .compile(&["../../shared/protos/auditor.proto"], &["../../shared/protos"])
        .unwrap();
}

```


* **成员 B (Java):**
在 `services/engine-java/pom.xml` 中配置 `protobuf-maven-plugin`，指向 `../../shared/protos` 路径。
* **成员 C (Python):**
使用 `uv` 运行生成命令：
```bash
uv run python -m grpc_tools.protoc -I../../shared/protos --python_out=. --grpc_python_out=. ../../shared/protos/auditor.proto

```


* **成员 D (Go):**
在网关目录下运行：
```bash
protoc --go_out=. --go-grpc_out=. ../../shared/protos/auditor.proto

```



---

## 3. 协作契约：版本变更规范

在开发过程中，如果需要增加字段（例如成员 C 想让模型返回具体的错别字坐标），**必须遵守以下红线**：

1. **禁止修改已有字段的编号：** 例如 `string text = 4;` 永远不能改成 `5`，否则会造成二进制不兼容。
2. **只能新增字段：** 如果需要新功能，添加 `string new_field = 6;`。
3. **由成员 D 统一更新：** 任何人需要改协议，必须向成员 D 提出申请，由 D 更新 `shared/protos` 后，大家再次同步。

---

## 4. 下一步

尝试让 A、B、C 启动一个最简单的“Hello gRPC”服务，验证大家是否能互相“ping”通。
