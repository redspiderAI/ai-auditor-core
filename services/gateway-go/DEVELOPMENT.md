# 开发文档 - 分布式调度网关

## 项目结构

```
gateway-go/
├── main.go                 # 应用入口点
├── go.mod                  # Go模块定义
├── go.sum                  # 依赖校验和
├── Dockerfile              # Docker构建配置
├── README.md               # 项目说明
├── API.md                  # API文档
├── .env.example            # 环境变量示例
├── Makefile                # 构建脚本
├── test.ps1                # 测试脚本
├── k8s-deployment.yaml     # Kubernetes部署配置
├── src/
│   ├── handlers/           # HTTP请求处理器
│   │   └── http_handlers.go
│   │   └── http_handlers_test.go
│   ├── store/              # 任务存储管理
│   │   └── store.go
│   │   └── store_test.go
│   ├── worker/             # 后台任务处理器
│   │   └── local_worker.go
│   ├── tempmanager/        # 临时文件管理
│   │   └── temp_manager.go
│   ├── notification/       # 通知服务
│   │   └── notification_service.go
│   └── mock/               # 模拟依赖
│       └── auditor/
│           └── auditor.go
└── temp_docs/              # 临时文件目录
```

## 核心组件

### 1. Handlers (src/handlers/)
处理HTTP请求，包括：
- 文件上传
- 任务状态查询
- 报告获取
- 结果下载

### 2. Store (src/store/)
管理任务状态，包括：
- 任务创建、查询、更新
- 任务状态枚举定义
- 报告写入功能

### 3. Worker (src/worker/)
处理后台任务，包括：
- 模拟文档解析
- 模拟审计过程
- 生成报告和注释文档

### 4. TempManager (src/tempmanager/)
管理临时文件，包括：
- 文件生命周期管理
- 自动清理过期文件
- 磁盘空间监控

### 5. Notification (src/notification/)
提供实时通知，包括：
- WebSocket连接管理
- 任务状态推送
- 客户端连接池管理

## 构建与运行

### 本地开发
```bash
# 构建
go build

# 运行
./gateway-go

# 或直接运行
go run main.go
```

### 使用Docker
```bash
# 构建镜像
docker build -t gateway-go .

# 运行容器
docker run -p 8080:8080 gateway-go
```

### 使用Makefile
```bash
# 查看可用命令
make help

# 构建
make build

# 运行
make run

# 测试
make test
```

## 环境变量

- `GATEWAY_PORT`: 服务端口 (默认: 8080)
- `TEMP_DIR`: 临时文件目录 (默认: ./temp_docs)

## 测试

### 单元测试
```bash
# 运行所有测试
go test ./...

# 运行特定包的测试
go test ./src/handlers/
go test ./src/store/

# 运行详细测试
go test -v ./...
```

### 集成测试
使用提供的PowerShell脚本：
```powershell
.\test.ps1
```

## 扩展指南

### 添加新API端点
1. 在handlers包中创建新的处理器函数
2. 在main.go中注册路由
3. 如需要，更新API文档

### 修改任务处理逻辑
1. 在worker包中修改处理逻辑
2. 如需要，更新store包中的任务模型
3. 更新相关测试

### 添加中间件
1. 在main.go中使用echo.Use()添加中间件
2. 实现自定义中间件函数

## 部署

### Docker部署
使用提供的Dockerfile构建镜像并部署到容器平台。

### Kubernetes部署
使用提供的k8s-deployment.yaml部署到Kubernetes集群。

## 最佳实践

1. **错误处理**: 始终检查错误并适当处理
2. **资源管理**: 确保关闭文件、连接等资源
3. **并发安全**: 使用互斥锁保护共享资源
4. **日志记录**: 记录重要事件和错误
5. **测试覆盖**: 为新功能编写单元测试
6. **代码风格**: 遵循Go语言规范和项目约定

## 故障排除

### 常见问题

1. **端口被占用**: 检查GATEWAY_PORT环境变量
2. **临时文件堆积**: 检查tempmanager的清理机制
3. **内存泄漏**: 检查goroutine是否正确关闭
4. **连接问题**: 检查网络配置和防火墙设置

### 调试技巧

1. 使用`go run -race`检测竞态条件
2. 使用pprof分析性能
3. 检查日志输出
4. 使用调试工具如dlv