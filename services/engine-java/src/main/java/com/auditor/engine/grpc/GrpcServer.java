package com.auditor.engine.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * gRPC 服务器启动器
 * 监听端口: 9191
 */
@Component
public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    private Server server;
    private final int port = 9191; // gRPC 服务监听端口

    /**
     * 在 Spring Boot 启动后自动开启 gRPC 服务
     */
    @PostConstruct
    public void start() throws IOException {
        // Java 21 虚拟线程：每个 gRPC 请求使用独立虚拟线程，支持高并发审计
        server = ServerBuilder.forPort(port)
                .addService(new DocumentAuditorServiceImpl())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build()
                .start();

        logger.info("gRPC Server 已成功启动，监听端口: {}", port);
        logger.info("已启用 Java 21 虚拟线程池，支持高并发审计请求");
    }

    /**
     * 在 Spring Boot 关闭前优雅停机
     */
    @PreDestroy
    public void stop() {
        if (server != null) {
            logger.info("正在关闭 gRPC Server...");
            server.shutdown();
            logger.info("gRPC Server 已关闭。");
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // 独立运行入口
        GrpcServer server = new GrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}