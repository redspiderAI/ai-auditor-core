# parser-rs

高性能文档解析与坐标定位引擎（成员A 模块）骨架。包含基本模块：`parser` / `layout` / `writer` / `grpc`。

快速开始：

1. 进入目录：

```
cd services/parser-rs
```

1. 构建：

```
cargo build
```

后续：实现 `parser::Parser`、`layout::DocumentTree`、`writer::Writer`，并用 `tonic` 实现 gRPC 服务。
