package com.auditor.engine.service.checkers;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格跨页检查器
 * 
 * 功能：检查表格是否跨页断开，是否有"续表"标志
 * 规则：跨页表格必须标注"续表"
 * 严重程度：HIGH
 */
@Component
public class TablePageBreakChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(TablePageBreakChecker.class);
    
    /**
     * 检查表格跨页
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> checkTablePageBreak(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || data.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }
        
        try {
            for (Section section : data.getSectionsList()) {
                // 识别表格元素
                if ("table".equals(section.getType())) {
                    String pageBreakStr = section.getPropsMap().getOrDefault("pageBreak", "false");
                    boolean pageBreak = Boolean.parseBoolean(pageBreakStr);
                    
                    // 如果表格跨页，检查是否有续表标志
                    if (pageBreak) {
                        String continuationFlag = section.getPropsMap().get("continuationFlag");
                        String tableCaption = section.getPropsMap().get("caption");
                        
                        // 检查是否有续表标志或标题中包含"续表"
                        boolean hasContinuationFlag = continuationFlag != null && !continuationFlag.isEmpty();
                        boolean captionHasContinuation = tableCaption != null && 
                            (tableCaption.contains("续表") || tableCaption.contains("continued"));
                        
                        if (!hasContinuationFlag && !captionHasContinuation) {
                            Issue issue = Issue.newBuilder()
                                    .setCode("FMT_TABLE_001")
                                    .setMessage("跨页表格必须标注'续表'标志")
                                    .setSectionId(section.getSectionId())
                                    .setSeverity(Severity.HIGH)
                                    .setSuggestion("在表格标题或属性中添加'续表'标志")
                                    .setOriginalSnippet(section.getText().length() > 100 ? 
                                        section.getText().substring(0, 100) + "..." : section.getText())
                                    .build();
                            issues.add(issue);
                            logger.debug("发现表格跨页问题: {}", section.getSectionId());
                        }
                    }
                }
            }
            
            logger.info("表格跨页检查完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("表格跨页检查异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_TABLE_CHECK")
                    .setMessage("表格跨页检查异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
}
