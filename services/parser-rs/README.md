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
  "references": [
    { "ref_id": "[1]", "raw_text": "完整引用文本" }
  ]
}
```

## 快速开始

1. 进入目录：
```
cd services/parser-rs
```

2. 构建：
```
cargo build
```

3. 运行gRPC服务：
```
cargo run
```

## 服务接口

通过gRPC提供以下服务：
- `ParseDocument`: 解析Word文档并返回结构化数据
- `InjectComments`: 在文档中注入评论

## 模块说明

- `parser`: 基础解析接口
- `docx_parser`: Word文档解析实现
- `layout`: 文档布局模型
- `layout_modeler`: 布局树构建器
- `grpc_service`: gRPC服务实现
- `parsed_data_converter`: 转换为标准输出格式
- `document`: protobuf生成的结构定义

## 与其他服务的协作

1. 成员A（本模块）：解析Word文档并转换为标准结构
2. 成员B（Java规则引擎）：基于解析结果执行格式规则检查
3. 成员C（Python语义分析）：对解析结果进行语义分析
4. 成员D（Go网关）：协调各服务并聚合结果
