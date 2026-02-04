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