# parser-rs

高性能文档解析与坐标定位引擎（成员A 模块）骨架。包含基本模块：`parser` / `layout` / `writer` / `grpc`。

快速开始：

1) 进入目录并构建/运行（当前默认启动占位 TCP 监听，不依赖 proto 生成）：

```bash
cd services/parser-rs
cargo run --bin parser_rs   # 注意是下划线，不是连字符
```

启动后会先批处理扫描仓库根目录下 `data/input` 中文件名以 “毕业论文.docx” 结尾的文档，解析并将结果写入 `data/output`（同名 `_parsed.json`）。

默认端口：

- gRPC 占位监听：52051（可通过 `RUST_GRPC_PORT` 覆盖）
- 健康监听：50051（可通过 `RUST_HEALTH_PORT` 覆盖）

1) 如需启用真实 tonic gRPC 服务（需要先生成 proto 代码后再启用）：

```bash
cargo run --bin parser_rs --features with-proto
```

注意：`with-proto` 依赖 `tonic::include_proto!("academic.auditor")`，需要先把 `shared/protos/auditor.proto` 编译到本工程的生成路径（尚未在脚本中自动生成）。

Windows 安装 protoc 提示：

- 下载安装包：https://github.com/protocolbuffers/protobuf/releases （选择 `protoc-*-win64.zip`）
- 解压后将其中的 `bin/protoc.exe` 加入 PATH，或在命令行运行前设置环境变量，例如：

```powershell
set PROTOC=C:\tools\protoc\bin\protoc.exe
```

注意：build.rs 会优先读取 PROTOC 环境变量，否则调用系统 PATH 中的 `protoc`。

后续：实现 `parser::Parser`、`layout::DocumentTree`、`writer::Writer`，并补全 gRPC 服务逻辑。
