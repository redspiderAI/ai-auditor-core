# 使用官方Golang镜像作为构建环境（升级以减少已知漏洞）
FROM golang:1.22.7-bookworm AS builder

# 设置工作目录
WORKDIR /app

# 配置国内代理以加速依赖下载，可按需替换
RUN go env -w GO111MODULE=on \
    && go env -w GOPROXY=https://mirrors.aliyun.com/goproxy/,direct

# 复制go.mod和go.sum文件
COPY go.mod go.sum ./

# 下载依赖
RUN go mod download

# 复制源代码
COPY . .

# 构建应用
RUN CGO_ENABLED=0 GOOS=linux go build -o gateway .

# 使用 Alpine 作为运行时镜像
FROM alpine:latest

# 安装 ca-certificates 包以支持 HTTPS
RUN apk --no-cache add ca-certificates

# 创建非root用户
RUN addgroup -g 65532 nonroot &&\
    adduser -D -u 65532 -G nonroot nonroot

# 设置工作目录
WORKDIR /app

# 从构建阶段复制二进制文件
COPY --from=builder /app/gateway /app/gateway

# 更改文件权限给非root用户
RUN chown -R nonroot:nonroot /app

# 切换到非root用户
USER nonroot

# 暴露端口
EXPOSE 8080

# 运行应用
CMD ["./gateway"]