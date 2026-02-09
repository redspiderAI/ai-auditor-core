package com.auditor.engine.grpc;

import com.auditor.grpc.AuditRequest;
import com.auditor.grpc.AuditResponse;
import com.auditor.grpc.Issue;
import com.auditor.grpc.ParseRequest;
import com.auditor.grpc.ParsedData;
import com.auditor.grpc.SemanticRequest;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.auditor.engine.service.FormattingAuditor;
import com.auditor.engine.service.IntegrityScanner;
import com.auditor.engine.service.ReferenceChecker;;

@Component // 改为 Spring 组件，支持依赖注入
public class EmbeddedGrpcServer {
        private Server server;

        // 注入规则引擎服务
        @Autowired
        private FormattingAuditor formattingAuditor;

        @Autowired
        private ReferenceChecker referenceChecker;

        @Autowired
        private IntegrityScanner integrityScanner;

        private int port = 9192; // 默认端口

        public void start(int port) throws IOException {
                // Build method descriptors for three RPCs defined in auditor.proto
                MethodDescriptor<ParseRequest, ParsedData> parseMethod = MethodDescriptor
                                .<ParseRequest, ParsedData>newBuilder()
                                .setType(MethodDescriptor.MethodType.UNARY)
                                .setFullMethodName(
                                                MethodDescriptor.generateFullMethodName(
                                                                "academic.auditor.DocumentAuditor", "ParseDocument"))
                                .setRequestMarshaller(ProtoUtils.marshaller(ParseRequest.getDefaultInstance()))
                                .setResponseMarshaller(ProtoUtils.marshaller(ParsedData.getDefaultInstance()))
                                .build();

                MethodDescriptor<AuditRequest, AuditResponse> auditMethod = MethodDescriptor
                                .<AuditRequest, AuditResponse>newBuilder()
                                .setType(MethodDescriptor.MethodType.UNARY)
                                .setFullMethodName(
                                                MethodDescriptor.generateFullMethodName(
                                                                "academic.auditor.DocumentAuditor", "AuditRules"))
                                .setRequestMarshaller(ProtoUtils.marshaller(AuditRequest.getDefaultInstance()))
                                .setResponseMarshaller(ProtoUtils.marshaller(AuditResponse.getDefaultInstance()))
                                .build();

                MethodDescriptor<SemanticRequest, AuditResponse> semanticMethod = MethodDescriptor
                                .<SemanticRequest, AuditResponse>newBuilder()
                                .setType(MethodDescriptor.MethodType.UNARY)
                                .setFullMethodName(
                                                MethodDescriptor.generateFullMethodName(
                                                                "academic.auditor.DocumentAuditor", "AnalyzeSemantics"))
                                .setRequestMarshaller(ProtoUtils.marshaller(SemanticRequest.getDefaultInstance()))
                                .setResponseMarshaller(ProtoUtils.marshaller(AuditResponse.getDefaultInstance()))
                                .build();

                // Create service definition and handlers
                io.grpc.ServerServiceDefinition svc = io.grpc.ServerServiceDefinition
                                .builder("academic.auditor.DocumentAuditor")
                                .addMethod(parseMethod, ServerCalls.asyncUnaryCall(
                                                new ServerCalls.UnaryMethod<ParseRequest, ParsedData>() {
                                                        @Override
                                                        public void invoke(ParseRequest request,
                                                                        StreamObserver<ParsedData> responseObserver) {
                                                                // ParseDocument - 文档解析（占位）
                                                                // 只返回文件名，没有实际解析
                                                                ParsedData parsed = ParsedData
                                                                                .newBuilder()
                                                                                .setDocId(request.getFilePath())
                                                                                .setMetadata(com.auditor.grpc.DocumentMetadata
                                                                                                .newBuilder()
                                                                                                .setTitle("parsed:"
                                                                                                                + request.getFilePath())
                                                                                                .build())
                                                                                .build();
                                                                responseObserver.onNext(parsed);
                                                                responseObserver.onCompleted();
                                                        }
                                                }))

                                // .addMethod(auditMethod, ServerCalls.asyncUnaryCall(new
                                // ServerCalls.UnaryMethod<AuditRequest,
                                // AuditResponse>() {
                                // @Override
                                // public void invoke(AuditRequest request, StreamObserver<AuditResponse>
                                // responseObserver) {

                                // 规则审计服务 - 占位实现
                                // AuditResponse resp = AuditResponse.newBuilder().setScoreImpact(0.0f).build();
                                // responseObserver.onNext(resp);
                                // responseObserver.onCompleted();
                                // }
                                // }))

                                // 替换：规则审计服务
                                .addMethod(auditMethod, ServerCalls.asyncUnaryCall(
                                                new ServerCalls.UnaryMethod<AuditRequest, AuditResponse>() {
                                                        @Override
                                                        public void invoke(AuditRequest request,
                                                                        StreamObserver<AuditResponse> responseObserver) {
                                                                processAuditRequest(request, responseObserver);
                                                        }
                                                }))

                                .addMethod(semanticMethod,
                                                ServerCalls.asyncUnaryCall(
                                                                new ServerCalls.UnaryMethod<SemanticRequest, AuditResponse>() {
                                                                        @Override
                                                                        public void invoke(SemanticRequest request,
                                                                                        StreamObserver<AuditResponse> responseObserver) {
                                                                                // 语义分析服务 - 占位实现?
                                                                                AuditResponse resp = AuditResponse
                                                                                                .newBuilder()
                                                                                                .setScoreImpact(0.0f)
                                                                                                .build();
                                                                                responseObserver.onNext(resp);
                                                                                responseObserver.onCompleted();
                                                                        }
                                                                }))
                                .build();

                server = ServerBuilder.forPort(port)
                                .addService(svc)
                                .build()
                                .start();

                System.out.println("Embedded gRPC server started on port " + port);

                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        System.err.println("Shutting down gRPC server");
                        EmbeddedGrpcServer.this.stop();
                }));
        }

        public void stop() {
                if (server != null) {
                        server.shutdown();
                }
        }

        // 处理规则审计请求
        private void processAuditRequest(AuditRequest request,
                        StreamObserver<AuditResponse> responseObserver) {
                List<Issue> allIssues = new ArrayList<>();
                try {
                        ParsedData data = request.getData();

                        // 1. 执行格式检查
                        List<Issue> formattingIssues = formattingAuditor.checkFormatting(data);
                        allIssues.addAll(formattingIssues);

                        // 2. 执行引用检查
                        List<Issue> referenceIssues = referenceChecker.checkReferences(data);

                        allIssues.addAll(referenceIssues);

                        // 3. 执行完整性检查

                        List<Issue> integrityIssues = integrityScanner.scanIntegrity(data);
                        allIssues.addAll(integrityIssues);

                        // 4. 合并所有结果
                        AuditResponse.Builder responseBuilder = AuditResponse.newBuilder();

                        // 5. 返回响应
                        responseObserver.onNext(responseBuilder.build());
                        responseObserver.onCompleted();

                } catch (Exception e) {
                        System.err.println(" 规则审计执行失败: " + e.getMessage());
                        responseObserver.onError(e);
                }
        }

}
