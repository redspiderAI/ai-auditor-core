package com.auditor.engine.grpc;

import com.auditor.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * gRPC 集成测试
 * 验证 GrpcServer 在 9191 端口启动后，客户端能否成功调用 AuditRules 接口
 */
public class GrpcIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(GrpcIntegrationTest.class);
    private static GrpcServer grpcServer;
    private static ManagedChannel channel;
    private static DocumentAuditorGrpc.DocumentAuditorBlockingStub blockingStub;

    @BeforeAll
    public static void setup() throws IOException {
        // 1. 启动服务器
        grpcServer = new GrpcServer();
        grpcServer.start();

        // 2. 创建客户端通道，连接 9191 端口
        channel = ManagedChannelBuilder.forAddress("localhost", 9191)
                .usePlaintext()
                .build();

        // 3. 创建阻塞存根
        blockingStub = DocumentAuditorGrpc.newBlockingStub(channel);
    }

    @AfterAll
    public static void teardown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (grpcServer != null) {
            grpcServer.stop();
        }
    }

    @Test
    public void testAuditRulesRpc() {
        // 构造一个简单的请求
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc-001")
                .setMetadata(DocumentMetadata.newBuilder().setTitle("测试文档").build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("1. 错误标题")
                        .putProps("font-family", "SimSun") // 故意设错，一级标题应为黑体
                        .build())
                .build();

        AuditRequest request = AuditRequest.newBuilder()
                .setData(data)
                .setTargetRuleSet("GB/T7714")
                .build();

        // 发起 RPC 调用
        logger.info("客户端发起 AuditRules RPC 调用 (Port: 9191)...");
        AuditResponse response = blockingStub.auditRules(request);

        // 验证响应
        assertNotNull(response);
        logger.info("收到 RPC 响应，问题数量: {}, 扣分: {}", response.getIssuesCount(), response.getScoreImpact());
        
        // 验证是否检出了排版错误
        boolean foundFontError = response.getIssuesList().stream()
                .anyMatch(issue -> issue.getCode().contains("FMT_HEADING_FONT"));
        
        assertTrue(foundFontError, "应当检出一级标题字体错误");
    }
}