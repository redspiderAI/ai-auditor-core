# API 文档 - 分布式调度网关

## 概述

分布式调度网关提供RESTful API接口，用于上传文档、查询任务状态、获取报告和下载结果。

## 基础URL

`http://localhost:8080` (默认端口，可通过GATEWAY_PORT环境变量修改)

## 认证

当前版本无需认证。在生产环境中，应添加适当的认证机制。

## API端点

### 1. 提交审核任务

**POST** `/api/v1/audit`

提交文档审核任务。

#### 请求

- **Content-Type**: `multipart/form-data`
- **Body**:
  - `file`: 要审核的文档文件 (支持 .docx, .pdf, .txt 等格式)

#### 响应

- **成功响应 (202)**:
  ```json
  {
    "task_id": "string"
  }
  ```

- **错误响应 (400)**:
  ```json
  {
    "error": "file required"
  }
  ```

#### 示例

```bash
curl -X POST \
  -F "file=@document.docx" \
  http://localhost:8080/api/v1/audit
```

---

### 2. 查询任务状态

**GET** `/api/v1/tasks/{id}`

查询指定任务的状态。

#### 参数

- **id** (路径参数): 任务ID

#### 响应

- **成功响应 (200)**:
  ```json
  {
    "id": "string",
    "status": "string",
    "progress": 0-100,
    "source_path": "string",
    "annotated_path": "string",
    "report_path": "string",
    "created_at": "timestamp",
    "updated_at": "timestamp"
  }
  ```

#### 示例

```bash
curl http://localhost:8080/api/v1/tasks/abc123
```

---

### 3. 获取审查报告

**GET** `/api/v1/report/{id}`

获取指定任务的审查报告。

#### 参数

- **id** (路径参数): 任务ID

#### 响应

- **成功响应 (200)**:
  ```json
  {
    "task_id": "string",
    "status": "completed",
    "generated_at": "timestamp",
    "issues": []
  }
  ```

#### 示例

```bash
curl http://localhost:8080/api/v1/report/abc123
```

---

### 4. 下载结果

**GET** `/api/v1/download/{id}`

下载打包好的ZIP文件（包含PDF报告与修订Word文档）。

#### 参数

- **id** (路径参数): 任务ID

#### 响应

- **成功响应 (200)**: 返回ZIP格式的压缩包，包含：
  - `annotated.docx`: 带注释的Word文档
  - `report.json`: JSON格式的审查报告
- **任务未完成响应 (202)**: 空响应体

#### 示例

```bash
curl http://localhost:8080/api/v1/download/abc123 -o result.zip
```

---

### 5. WebSocket实时通知

**GET** `/ws/task/{id}`

建立WebSocket连接以接收任务状态的实时更新。

#### 参数

- **id** (路径参数): 任务ID

#### 消息格式

```json
{
  "type": "task_status_update | task_completed | task_error",
  "task_id": "string",
  "status": "string",
  "progress": 0-100,
  "timestamp": "timestamp",
  "message": "string"
}
```

---

### 6. 健康检查

**GET** `/health`

检查服务健康状态。

#### 响应

- **成功响应 (200)**:
  ```json
  {
    "status": "healthy",
    "service": "gateway-go"
  }
  ```

#### 示例

```bash
curl http://localhost:8080/health
```

## 状态码

- `200`: 成功
- `202`: 请求已接受，处理中
- `400`: 请求错误
- `404`: 资源未找到
- `500`: 服务器内部错误

## 任务状态

- `Pending`: 等待处理
- `Queued`: 已排队
- `Parsing`: 解析中
- `Auditing`: 审查中
- `Generating`: 生成报告中
- `Completed`: 已完成
- `Error`: 发生错误