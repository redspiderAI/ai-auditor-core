package com.auditor.engine.service;

import com.auditor.grpc.ParsedData;
import com.auditor.grpc.AuditResponse;

public class ReferenceChecker {

    // 占位：执行引用闭环一致性检查，返回 AuditResponse（暂为空实现）
    public AuditResponse checkReferences(ParsedData data) {
        // TODO: 实现引用匹配与一致性检查
        return AuditResponse.newBuilder().setScoreImpact(0.0f).build();
    }
}
