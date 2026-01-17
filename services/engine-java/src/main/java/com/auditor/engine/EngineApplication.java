package com.auditor.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        // Allow dynamic port from environment variable `JAVA_ENGINE_ADDR`.
        // Expected formats: "host:port", "port", or "hostname:port".
        String addr = System.getenv("JAVA_ENGINE_ADDR");
        if (addr != null && !addr.isBlank()) {
            String portStr = addr;
            int colon = addr.lastIndexOf(':');
            if (colon != -1 && colon < addr.length() - 1) {
                portStr = addr.substring(colon + 1);
            }
            try {
                int port = Integer.parseInt(portStr);
                System.setProperty("server.port", Integer.toString(port));
            } catch (NumberFormatException e) {
                // ignore and fall back to defaults
            }
        }

        // Determine gRPC port: prefer explicit env `JAVA_GRPC_PORT`, else use httpPort+1, else default 9192
        int grpcPort = 9192;
        String grpcEnv = System.getenv("JAVA_GRPC_PORT");
        if (grpcEnv != null && !grpcEnv.isBlank()) {
            try {
                grpcPort = Integer.parseInt(grpcEnv);
            } catch (NumberFormatException ignored) {
            }
        } else {
            String portProp = System.getProperty("server.port");
            int httpPort = 0;
            try {
                httpPort = Integer.parseInt(portProp);
            } catch (Exception ignored) {}
            if (httpPort > 0) grpcPort = httpPort + 1;
        }

        try {
            // Try to load EmbeddedGrpcServer reflectively to avoid a compile-time dependency
            Class<?> cls = Class.forName("com.auditor.engine.grpc.EmbeddedGrpcServer");
            Object grpcServer = cls.getDeclaredConstructor().newInstance();
            try {
                cls.getMethod("start", int.class).invoke(grpcServer, grpcPort);
            } catch (NoSuchMethodException nsme) {
                System.err.println("EmbeddedGrpcServer.start(int) not found: " + nsme.getMessage());
            }
        } catch (ClassNotFoundException e) {
            // Embedded gRPC server not present on classpath; skip starting it.
            System.err.println("Embedded gRPC server class not found; skipping start.");
        } catch (Exception e) {
            System.err.println("Failed to start embedded gRPC server: " + e.getMessage());
        }

        SpringApplication.run(EngineApplication.class, args);
        System.out.println("Engine-Java started (placeholder)");
    }
}
