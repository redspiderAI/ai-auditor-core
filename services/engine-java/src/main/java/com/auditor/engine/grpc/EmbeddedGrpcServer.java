package com.auditor.engine.grpc;

import com.auditor.grpc.AuditRequest;
import com.auditor.grpc.AuditResponse;
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

public class EmbeddedGrpcServer {
    private Server server;

    public void start(int port) throws IOException {
        // Build method descriptors for three RPCs defined in auditor.proto
        MethodDescriptor<ParseRequest, ParsedData> parseMethod = MethodDescriptor.<ParseRequest, ParsedData>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("academic.auditor.DocumentAuditor", "ParseDocument"))
                .setRequestMarshaller(ProtoUtils.marshaller(ParseRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(ParsedData.getDefaultInstance()))
                .build();

        MethodDescriptor<AuditRequest, AuditResponse> auditMethod = MethodDescriptor.<AuditRequest, AuditResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("academic.auditor.DocumentAuditor", "AuditRules"))
                .setRequestMarshaller(ProtoUtils.marshaller(AuditRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(AuditResponse.getDefaultInstance()))
                .build();

        MethodDescriptor<SemanticRequest, AuditResponse> semanticMethod = MethodDescriptor.<SemanticRequest, AuditResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("academic.auditor.DocumentAuditor", "AnalyzeSemantics"))
                .setRequestMarshaller(ProtoUtils.marshaller(SemanticRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(AuditResponse.getDefaultInstance()))
                .build();

        // Create service definition and handlers
        io.grpc.ServerServiceDefinition svc = io.grpc.ServerServiceDefinition.builder("academic.auditor.DocumentAuditor")
                .addMethod(parseMethod, ServerCalls.asyncUnaryCall(new ServerCalls.UnaryMethod<ParseRequest, ParsedData>() {
                    @Override
                    public void invoke(ParseRequest request, StreamObserver<ParsedData> responseObserver) {
                        ParsedData parsed = ParsedData.newBuilder()
                                .setDocId(request.getFilePath())
                                .setMetadata(com.auditor.grpc.DocumentMetadata.newBuilder().setTitle("parsed:" + request.getFilePath()).build())
                                .build();
                        responseObserver.onNext(parsed);
                        responseObserver.onCompleted();
                    }
                }))
                .addMethod(auditMethod, ServerCalls.asyncUnaryCall(new ServerCalls.UnaryMethod<AuditRequest, AuditResponse>() {
                    @Override
                    public void invoke(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
                        AuditResponse resp = AuditResponse.newBuilder().setScoreImpact(0.0f).build();
                        responseObserver.onNext(resp);
                        responseObserver.onCompleted();
                    }
                }))
                .addMethod(semanticMethod, ServerCalls.asyncUnaryCall(new ServerCalls.UnaryMethod<SemanticRequest, AuditResponse>() {
                    @Override
                    public void invoke(SemanticRequest request, StreamObserver<AuditResponse> responseObserver) {
                        AuditResponse resp = AuditResponse.newBuilder().setScoreImpact(0.0f).build();
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
}
