<<<<<<< HEAD
# 使用官方Golang镜像作为构建环境
FROM golang:1.22-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制go.mod和go.sum文件
COPY go.mod go.sum ./

# 下载依赖
RUN go mod download

# 复制源代码
COPY . .

# 构建应用
RUN CGO_ENABLED=0 GOOS=linux go build -o gateway .

# 使用轻量级的基础镜像运行应用
FROM alpine:latest

# 安装ca-certificates，以便应用可以进行HTTPS请求
RUN apk --no-cache add ca-certificates

# 创建非root用户
RUN adduser -D -s /bin/sh appuser

# 设置工作目录
WORKDIR /app

# 从构建阶段复制二进制文件
COPY --from=builder /app/gateway .

# 更改文件所有权给非root用户
RUN chown appuser:appuser gateway

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 运行应用
CMD ["./gateway"]
=======
FROM golang:1.22-bookworm AS builder
WORKDIR /src
COPY go.mod go.sum* ./
RUN go env -w GOPROXY=https://proxy.golang.org
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/gateway ./

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates netcat && rm -rf /var/lib/apt/lists/*
COPY --from=builder /out/gateway /usr/local/bin/gateway
EXPOSE 8080
VOLUME ["/app/temp_docs"]
HEALTHCHECK --interval=10s --timeout=3s CMD nc -z localhost 8080 || exit 1
CMD ["/usr/local/bin/gateway"]
>>>>>>> main
