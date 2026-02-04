# parser-rs

高性能文档解析与坐标定位引擎（成员A 模块）。将Word文档转换为标准化结构，供成员B（规则引擎）和成员C（语义分析）使用。

## 功能特性

- 解析Word文档（.docx）结构
- 提取文档内容（标题、段落、表格等）
- 识别格式属性（字体、大小、间距等）
- 提取引用信息
- 转换为标准化JSON结构
- 通过gRPC提供服务

## 输出结构

解析器将Word文档转换为以下结构，供其他服务使用：

```json
{
  "doc_id": "document_identifier",
  "metadata": {
    "title": "文档标题",
    "total_pages": 10,
    "global_style": { "font": "SimSun", "line_spacing": 1.5 }
  },
  "sections": [
    {
      "section_id": 1,
      "type": "heading",
      "level": 1,
      "text": "标题文本",
      "properties": { "font_size": 16, "bold": true }
    },
    {
      "section_id": 2,
      "type": "paragraph",
      "text": "段落文本",
      "citations": ["[1]"],
      "properties": { "first_line_indent": 2.0 }
    }
  ],
  "references": [{ "ref_id": "[1]", "raw_text": "完整引用文本" }]
}
```

## 快速开始

1. 进入目录并构建/运行（当前默认启动占位 TCP 监听，不依赖 proto 生成）：

```bash
cd services/parser-rs
cargo run --bin parser_rs   # 注意是下划线，不是连字符
```

启动后会先批处理扫描仓库根目录下 `data/input` 中文件名以 “毕业论文.docx” 结尾的文档，解析并将结果写入 `data/output`（同名 `_parsed.json`）。

默认端口：

- gRPC 占位监听：52051（可通过 `RUST_GRPC_PORT` 覆盖）
- 健康监听：50051（可通过 `RUST_HEALTH_PORT` 覆盖）

1. 如需启用真实 tonic gRPC 服务（需要先生成 proto 代码后再启用）：

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
