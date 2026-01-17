# 📄 需求文档：高性能文档解析与坐标定位引擎 (Rust 模块)

## 1. 任务概述

成员 A 负责开发整个系统的“数字传感器”。该模块需将非结构化的 Word (.docx) 或 PDF 文件，解析为一套标准化的、带有精确物理属性（字体、坐标、层级）的结构化数据集。此外，该模块还需负责在审查结束后，将错误信息回写至原文档生成批注。

### 2. 技术栈要求

* **核心语言：** **Rust** (Edition 2021)
* **关键库：** * `docx-rs` / `dotext`: 用于高效解析 OpenXML 结构。
* `quick-xml`: 高性能处理 XML 流。
* `tonic` / `prost`: 实现高性能 gRPC 服务与 Protobuf 序列化。
* `rayon`: 利用多核 CPU 并行处理文档的不同节（Sections）。

* **构建工具：** `cargo` + `cross` (用于跨平台交叉编译为 Docker 镜像)。

---

### 3. 核心功能模块与执行清单

#### 模块一：OpenXML 深度解析引擎 (Structural Parser)

* **需求描述：** 将 `.docx` 文件拆解为带有唯一 ID 的段落、标题、表格和公式。
* **任务拆解：**
* **样式提取：** 穿透解析 `word/styles.xml`，获取每个段落的继承样式（如：如果正文没设字体，需溯源至全局默认字体）。
* **属性映射：** 提取缩进（ind）、行间距（lineSpacing）、字号（sz）等关键度量值。

* **执行清单：** 编写 `Parser` Trait，实现对 Paragraph、Run、Table 等元素的递归解析。

#### 模块二：物理坐标与排版建模 (Layout Modeler)

* **需求描述：** 为后续的“格式审查”提供物理数据支持。
* **任务拆解：**
* **层级识别：** 识别 `w:outlineLvl`，为每个 Heading 分配正确的 Level。
* **对象定位：** 标记每个 Section 在原 XML 中的偏移量（Offset），以便后续回写。

* **执行清单：** 构建一个内存中的 `DocumentTree` 数据结构。

#### 模块三：双向回写与批注生成 (Annotation Injector)

* **需求描述：** 根据成员 D 传回的 Issue 列表，在原文档中“无损”插入批注。
* **任务拆解：**
* **XML 注入：** 在目标 `w:r` 标签附近插入 `w:commentReference`。
* **评论同步：** 在 `word/comments.xml` 中生成对应的评论内容。

* **执行清单：** 实现 `Writer` 模块，确保回写后的文档能够被 Microsoft Word 正常打开而不报“文件损坏”。

---

### 4. 关键数据模型 (Rust 实现参考)

成员 A 需定义如下核心模型，并负责将其序列化为 gRPC 消息：

```rust
pub struct DocumentSection {
    pub id: i32,
    pub element_type: ElementType, // Heading, Paragraph, etc.
    pub raw_text: String,
    pub formatting: HashMap<String, String>, // 存放如 "font-size": "12pt"
    pub xml_path: String, // 用于回写定位的内部路径
}

pub enum ElementType {
    Heading(u8),
    Paragraph,
    Table,
    Equation,
}

```

---

### 5. 接口契约与性能指标

| 输入内容 | 处理逻辑 | 输出内容 |
| --- | --- | --- |
| `.docx` 文件流 | 线性扫描 + XML 解构 | `ParsedData` (Protobuf 格式) |
| `Issue` 列表 + 原文件 | XML 节点插入 | 修订版 `.docx` 文件 |

* **性能 KPI：** * 解析 10 万字文档（含 50+ 表格）耗时需控制在 **500ms** 以内。
* 内存占用在峰值时不应超过 **200MB**。

---

### 💡 协作建议

**成员 A 请注意：** 你是系统唯一的“物理坐标”提供者。

* 请务必妥善处理 **XML 命名空间 (Namespaces)**，这是导致回写文档损坏的最常见原因。
* 考虑使用 **Memory Mapping (mmap)** 来读取超大型文件，以进一步提升 I/O 效率。
