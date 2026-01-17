# gRPC通讯协议

---

## 1. 成员执行指南

各成员需要根据此 `.proto` 文件使用对应的编译器生成代码：

* **成员 A (Rust):** 使用 `tonic-build`。在 `build.rs` 中配置编译该文件。
* **成员 B (Java):** 使用 `protoc-jar-maven-plugin`。Maven 构建时会自动生成 POJO 对象。
* **成员 C (Python):** 使用 `grpcio-tools`。运行 `python -m grpc_tools.protoc` 生成 `_pb2.py`。
* **成员 D (Go):** 使用 `protoc-gen-go` 和 `protoc-gen-go-grpc`。

---

## 2. 关键设计逻辑说明

1. **Section ID 锚点：** `section_id` 是贯穿全流程的“灵魂”。Rust 负责生成它，Java 和 Python 负责针对这个 ID 报错，Go 负责根据这个 ID 指示 Rust 在原文档哪个位置画批注。
2. **Map 扩展性：** `Section` 中的 `props` 使用 `map<string, string>`，这样当成员 A 想增加新的检测维度（如行高、间距）时，不需要修改 `.proto` 结构，兼容性极强。
3. **Severity 分级：** 明确了错误的严重程度，方便成员 D 在生成最后的 PDF 报告时进行颜色编码（Critical 为红色，Info 为灰色）。

---
