# AI 审计系统基础设施设置指南

本指南介绍如何部署完整的AI审计系统基础设施，包括Redis、Prometheus监控和可选的Argo工作流。

## 架构概述

- **Go 网关服务**: 处理文件上传、任务调度和状态管理
- **Redis**: 任务队列和状态存储 (使用 Redis Streams)
- **Prometheus**: 系统监控和指标收集
- **Grafana**: 指标可视化 (可选)
- **Argo Workflows**: 工作流编排 (可选)

## 系统要求

- Docker Desktop 或 Docker Engine
- Docker Compose v2+

## 快速部署

### 1. 部署基础服务

```bash
# 启动所有基础设施服务
docker-compose -f docker-compose.infra.yml up -d
```

### 2. 验证部署

服务启动后，您可以通过以下方式验证：

- 网关健康检查: `http://localhost:8080/health`
- 网关指标: `http://localhost:8080/metrics`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)

## 配置说明

### Redis 配置

- **队列**: 使用 Redis Streams 实现任务队列
- **消费者组**: `gateway_group`
- **消费者**: `gateway_consumer`
- **流名称**: `audit_tasks`

### Prometheus 监控指标

系统暴露以下指标：

- `gateway_requests_total`: 请求总数
- `gateway_request_duration_seconds`: 请求持续时间
- `gateway_tasks_processed_total`: 处理的任务总数
- `gateway_active_tasks`: 活跃任务数
- `gateway_errors_total`: 错误总数

### 环境变量配置

| 变量 | 描述 | 默认值 |
|------|------|--------|
| REDIS_ADDR | Redis 地址 | redis:6379 |
| REDIS_STREAM | Redis 流名称 | audit_tasks |
| REDIS_GROUP | Redis 消费者组 | gateway_group |
| REDIS_CONSUMER | Redis 消费者 | gateway_consumer |
| METRICS_ENABLED | 启用指标收集 | true |

## Argo Workflows 集成

如果需要使用 Argo Workflows 进行更复杂的工作流编排：

### 1. 安装 Argo Workflows

```bash
# 安装 Argo Workflows 控制器和服务
kubectl create ns argo
kubectl apply -n argo -f https://github.com/argoproj/argo-workflows/releases/latest/download/install.yaml

# 安装 Argo CLI (可选)
curl -sLO https://github.com/argoproj/argo-workflows/releases/latest/download/argo-windows-amd64.gz
gunzip argo-windows-amd64.gz
chmod +x argo-windows-amd64
sudo mv argo-windows-amd64 /usr/local/bin/argo
```

### 2. 提交工作流

```bash
# 提交示例工作流
argo submit -n argo argo-workflow-example.yaml -p document-path="/tmp/sample.pdf"
```

## 监控和告警

### Prometheus 查询示例

```promql
# 请求速率
sum(rate(gateway_requests_total[5m])) by (method, endpoint)

# 平均请求延迟
histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le, method, endpoint))

# 错误率
sum(rate(gateway_errors_total[5m])) by (type, source) / sum(rate(gateway_requests_total[5m])) by (method, endpoint)
```

### Grafana 面板配置

1. 登录 Grafana (admin/admin)
2. 添加 Prometheus 数据源 (http://prometheus:9090)
3. 导入预设的仪表板 JSON 文件

## 故障排除

### 检查服务状态

```bash
# 查看所有服务
docker-compose -f docker-compose.infra.yml ps

# 查看日志
docker-compose -f docker-compose.infra.yml logs -f gateway
docker-compose -f docker-compose.infra.yml logs -f redis
```

### Redis 连接问题

如果网关无法连接到 Redis，请检查：
1. Redis 服务是否正在运行
2. 环境变量 `REDIS_ADDR` 是否正确配置
3. 网络连接是否正常

### 监控指标不可用

如果无法获取指标，请检查：
1. `/metrics` 端点是否可访问
2. Prometheus 配置是否正确
3. 防火墙设置

## 生产环境建议

1. **安全性**:
   - 为 Redis 设置密码认证
   - 限制外部访问 Prometheus 和 Grafana
   - 使用 HTTPS 加密通信

2. **性能**:
   - 根据负载调整 Redis 内存限制
   - 配置 Prometheus 数据保留策略
   - 调整网关的最大队列大小

3. **高可用性**:
   - 部署 Redis 集群
   - 配置 Prometheus 联邦
   - 使用负载均衡器部署多个网关实例