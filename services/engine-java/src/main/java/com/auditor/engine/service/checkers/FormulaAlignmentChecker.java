package com.auditor.engine.service.checkers;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式对齐检查器
 * 
 * 功能：检查数学公式是否右对齐
 * 规则：所有公式必须右对齐
 * 严重程度：MEDIUM
 */
@Component
public class FormulaAlignmentChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(FormulaAlignmentChecker.class);
    
    /**
     * 检查公式对齐
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> checkFormulaAlignment(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || data.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }
        
        try {
            for (Section section : data.getSectionsList()) {
                // 识别公式元素
                if ("formula".equals(section.getType())) {
                    String alignment = section.getPropsMap().getOrDefault("alignment", "left");
                    
                    // 检查是否右对齐
                    if (!"right".equals(alignment) && !"center".equals(alignment)) {
                        Issue issue = Issue.newBuilder()
                                .setCode("FMT_FORMULA_001")
                                .setMessage("公式必须右对齐或居中对齐，当前对齐方式: " + alignment)
                                .setSectionId(section.getSectionId())
                                .setSeverity(Severity.MEDIUM)
                                .setSuggestion("将公式对齐方式改为右对齐或居中对齐")
                                .setOriginalSnippet(section.getText().length() > 100 ? 
                                    section.getText().substring(0, 100) + "..." : section.getText())
                                .build();
                        issues.add(issue);
                        logger.debug("发现公式对齐问题: {}", section.getSectionId());
                    }
                }
            }
            
            logger.info("公式对齐检查完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("公式对齐检查异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_FORMULA_CHECK")
                    .setMessage("公式对齐检查异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
}
