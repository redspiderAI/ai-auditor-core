package com.auditor.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类
 * HTTP 端口: 8081
 * gRPC 端口: 9191 (由 GrpcServer 类管理)
 */
@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
        System.out.println("AI Auditor Engine-Java 启动成功！");
        System.out.println("HTTP 服务: http://localhost:8081");
        System.out.println("gRPC 服务: localhost:9191");
    }
}