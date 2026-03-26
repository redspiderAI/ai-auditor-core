package com.auditor.engine.service.checkers;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 页边距检查器
 * 
 * 功能：检查页面边距是否符合标准（上下左右各 2.5cm）
 * 规则：页边距必须为 2.5cm
 * 严重程度：MEDIUM
 */
@Component
public class PageMarginChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(PageMarginChecker.class);
    private static final float EXPECTED_MARGIN = 2.5f; // cm
    private static final float TOLERANCE = 0.1f; // 容差 0.1cm
    
    /**
     * 检查页边距
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> checkPageMargins(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || !data.hasMetadata()) {
            logger.warn("输入数据为空或缺少元数据");
            return issues;
        }
        
        try {
            DocumentMetadata metadata = data.getMetadata();
            
            // 从 proto 中直接获取页边距
            float topMargin = metadata.getMarginTop();
            float bottomMargin = metadata.getMarginBottom();
            
            // 如果 proto 中没有左右边距，使用默认值
            float leftMargin = EXPECTED_MARGIN;
            float rightMargin = EXPECTED_MARGIN;
            
            // 检查上边距
            if (topMargin > 0 && !isMarginValid(topMargin)) {
                Issue issue = Issue.newBuilder()
                        .setCode("FMT_MARGIN_001")
                        .setMessage("上边距应为 2.5cm，当前为 " + String.format("%.2f", topMargin) + "cm")
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("将上边距调整为 2.5cm")
                        .build();
                issues.add(issue);
                logger.debug("发现上边距问题");
            }
            
            // 检查下边距
            if (bottomMargin > 0 && !isMarginValid(bottomMargin)) {
                Issue issue = Issue.newBuilder()
                        .setCode("FMT_MARGIN_002")
                        .setMessage("下边距应为 2.5cm，当前为 " + String.format("%.2f", bottomMargin) + "cm")
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("将下边距调整为 2.5cm")
                        .build();
                issues.add(issue);
                logger.debug("发现下边距问题");
            }
            
            logger.info("页边距检查完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("页边距检查异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_MARGIN_CHECK")
                    .setMessage("页边距检查异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
    
    /**
     * 检查边距是否有效（在容差范围内）
     */
    private boolean isMarginValid(float margin) {
        return Math.abs(margin - EXPECTED_MARGIN) <= TOLERANCE;
    }
}
