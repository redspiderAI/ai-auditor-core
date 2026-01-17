# gateway-go

简单的 HTTP 网关骨架，实现文件上传、任务查询、报告与下载接口，并包含一个本地后台 worker 模拟调用 A/B/C 服务。

快速运行（本地）：

```powershell
cd services/gateway-go
go build
./gateway
```

上传示例（curl）：

```bash
curl -F "file=@../services/parser-rs/data/example.docx" http://localhost:8080/api/v1/upload
```

说明：此实现为最小可运行原型，后续应接入真实 gRPC 客户端（基于 `shared/protos/auditor.proto`）并替换 worker 中的模拟步骤。
