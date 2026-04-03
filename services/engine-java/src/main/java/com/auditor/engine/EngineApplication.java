package com.auditor.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Startup Class
 * HTTP Port: 8081
 * gRPC Port: 9191 (Managed by GrpcServer class)
 */
@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
        System.out.println("AI Auditor Engine-Java started successfully!");
        System.out.println("HTTP Service: http://localhost:8081");
        System.out.println("gRPC Service: localhost:9191");
    }
}