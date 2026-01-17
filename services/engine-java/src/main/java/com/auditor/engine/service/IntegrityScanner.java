package com.auditor.engine.service;

import com.auditor.grpc.ParsedData;
import com.auditor.grpc.AuditResponse;

public class IntegrityScanner {

    // 占位：检查文档必备章节并返回 AuditResponse
    public AuditResponse scanIntegrity(ParsedData data) {
        // TODO: 实现状态机与章节完整性检查
        return AuditResponse.newBuilder().setScoreImpact(0.0f).build();
    }
}
