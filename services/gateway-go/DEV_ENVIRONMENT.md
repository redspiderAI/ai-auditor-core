# 开发环境配置指南

## Go构建标签配置

本项目使用Go构建标签来支持不同的部署模式：

- `grpc` 标签：启用gRPC worker，用于生产环境
- 默认（无标签）：使用本地模拟worker，用于开发环境

## VS Code配置

项目已预配置了 `.vscode/settings.json` 文件，包含构建标签设置。

## 手动配置方法

如果VS Code仍然无法正确解析文件，可以尝试以下方法：

### 方法1：全局设置
在VS Code设置中添加：
```json
{
    "go.buildTags": "grpc",
    "go.toolsEnvVars": {
        "GOFLAGS": "-tags=grpc"
    }
}
```

### 方法2：环境变量
在终端中设置环境变量：
```bash
# Linux/MacOS
export GOFLAGS="-tags=grpc"

# Windows (PowerShell)
$env:GOFLAGS = "-tags=grpc"

# Windows (CMD)
set GOFLAGS=-tags=grpc
```

## 构建命令

### 开发模式（默认）
```bash
go build
# 或
go run main.go
```

### 生产模式（gRPC）
```bash
go build -tags grpc
# 或
go run -tags grpc main.go
```

## 文件说明

- `src/worker/local_worker.go` - //go:build !grpc - 本地模拟模式
- `src/worker/grpc_worker.go` - //go:build grpc - gRPC生产模式

根据构建标签，Go工具会选择相应的文件进行编译。