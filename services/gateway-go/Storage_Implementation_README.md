# 任务存储实现说明

## 概述

本项目实现了可插拔的任务存储机制，支持内存存储和Redis持久化存储两种方式。通过接口抽象，可以在运行时根据配置动态选择存储实现。

## 存储接口

```go
type TaskStore interface {
    Save(task *Task) error
    Get(taskID string) (*Task, bool)
    Update(taskID string, updater func(*Task)) error
    Delete(taskID string) error
}
```

## 实现详情

### 1. 内存存储 (MemoryStore)

- **位置**: `src/store/store.go`
- **特点**: 
  - 快速读写，适合开发和测试环境
  - 数据随进程结束而丢失
  - 线程安全的读写锁保护

### 2. Redis存储 (RedisStore)

- **位置**: `src/store/redis_store.go`
- **特点**:
  - 支持数据持久化，服务重启后数据不丢失
  - 支持分布式部署，多个实例可共享任务状态
  - 可配置TTL（Time-To-Live），自动清理过期任务
  - 故障时自动回退到内存存储

## 配置选项

通过环境变量控制存储类型：

```bash
# 选择存储类型 (默认: memory)
STORAGE_TYPE=redis          # 使用Redis存储
# STORAGE_TYPE=memory       # 使用内存存储

# Redis相关配置
REDIS_ADDR=localhost:6379   # Redis服务器地址
TASK_TTL_HOURS=24           # 任务在Redis中的存活时间（小时）
```

## 架构优势

1. **接口抽象**: 通过`TaskStore`接口解耦了存储实现，便于扩展新的存储方式
2. **向后兼容**: 保留内存存储作为默认和备选方案
3. **故障回退**: Redis不可用时自动回退到内存存储，保证服务可用性
4. **配置灵活**: 通过环境变量动态控制存储行为
5. **类型安全**: 使用Go的强类型系统确保接口一致性

## 使用场景

- **开发/测试环境**: 使用`STORAGE_TYPE=memory`获得快速响应
- **生产环境**: 使用`STORAGE_TYPE=redis`确保数据持久化和高可用性
- **灰度发布**: 可以在不同实例间混用存储类型进行渐进式迁移

## 代码变更影响

此实现对现有代码的影响最小：
- HTTP处理器现在接收`store.TaskStore`接口而不是具体类型
- Worker函数现在使用接口的`Update`方法替代原来的`UpdateTask`
- 主程序根据配置动态创建存储实例
- 所有其他业务逻辑保持不变