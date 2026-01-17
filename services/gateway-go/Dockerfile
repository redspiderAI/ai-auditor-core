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
