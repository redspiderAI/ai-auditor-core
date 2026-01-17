package com.auditor.engine.service;

import com.auditor.grpc.Issue;
import com.auditor.grpc.ParsedData;

import java.util.List;
import java.util.ArrayList;

public class FormattingAuditor {

    // 简单占位方法：接受 ParsedData，返回发现的问题列表（POJO Issue）
    public List<Issue> checkFormatting(ParsedData data) {
        // TODO: 集成 Drools 规则引擎或具体实现
        return new ArrayList<>();
    }

}
