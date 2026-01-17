# Inference-Py

轻量的 AI 审查与模型中台服务（Python）。本目录包含用于语义审查、文献核验与长文本一致性检查的代码骨架与测试脚本。

项目结构（工作区相对路径）

- [main.py](main.py): 启动脚本（项目入口示例）
- [pyproject.toml](pyproject.toml): 项目元数据与 `uv` 配置
- [model/](model): 本地模型或模型配置目录（当前为空）
- [data/](data): 本地数据目录（当前为空）
- [src/](src): 源代码（模块实现位于此）
  - [src/semantic_detection](src/semantic_detection)
  - [src/fact_checking](src/fact_checking)
  - [src/logic_consistency](src/logic_consistency)
- [tests/](tests): 简单的运行/功能测试（含 `torch` 测试）

主要模块

- semantic_detection: 语义、用词与标点检测
- fact_checking: 参考文献检索与真伪比对（RAG）
- logic_consistency: 长文本前后逻辑一致性检查

快速开始

1. 使用 `uv` 创建虚拟环境（推荐）：

```powershell
uv venv
```

1. 安装必要依赖（示例）：

```powershell
uv add torch fastapi langgraph
```

说明：`milvus` 在 Windows 上可能没有可用 wheel，安装可能会因平台不匹配失败；遇到错误可参考下文说明。

运行测试

- 直接运行（不通过 `uv`）：

```powershell
python tests/torch_test.py
```

- 通过 `uv` 运行（当使用 `uv` 管理虚拟环境与依赖时）：

```powershell
uv run ./tests/torch_test.py
```

设备选择（GPU/CPU）

- 命令行覆盖：

```powershell
uv run ./tests/torch_test.py -- --device cuda:0
```

- 环境变量覆盖：

```powershell
set TORCH_DEVICE=cuda:0
uv run ./tests/torch_test.py
```

如果不指定设备，脚本会在运行时优先选择可用的 CUDA（GPU），否则回退到 CPU。
