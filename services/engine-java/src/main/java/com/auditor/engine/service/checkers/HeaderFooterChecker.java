package com.auditor.engine.service.checkers;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 页眉页脚检查器
 * 
 * 功能：检查页眉是否包含文档标题，页脚是否包含页码
 * 规则：页眉应包含标题，页脚应包含页码
 * 严重程度：LOW
 */
@Component
public class HeaderFooterChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(HeaderFooterChecker.class);
    
    /**
     * 检查页眉页脚
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> checkHeaderFooter(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || !data.hasMetadata()) {
            logger.warn("输入数据为空或缺少元数据");
            return issues;
        }
        
        try {
            DocumentMetadata metadata = data.getMetadata();
            String title = metadata.getTitle();
            
            // 注：由于 proto 中没有定义 header 和 footer 字段，
            // 这里只进行基础检查。实际应用中应从 Section 中提取页眉页脚信息
            
            if (title.isEmpty()) {
                logger.warn("文档标题为空，跳过页眉页脚检查");
                return issues;
            }
            
            // 建议添加页眉
            Issue headerIssue = Issue.newBuilder()
                    .setCode("FMT_HEADER_001")
                    .setMessage("建议在页眉中添加文档标题")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("添加页眉，包含文档标题: " + title)
                    .build();
            issues.add(headerIssue);
            logger.debug("建议添加页眉");
            
            // 建议添加页脚
            Issue footerIssue = Issue.newBuilder()
                    .setCode("FMT_FOOTER_001")
                    .setMessage("建议在页脚中添加页码")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("添加页脚，包含页码标记")
                    .build();
            issues.add(footerIssue);
            logger.debug("建议添加页脚");
            
            logger.info("页眉页脚检查完成，发现 {} 个建议", issues.size());
            
        } catch (Exception e) {
            logger.error("页眉页脚检查异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_HEADER_FOOTER_CHECK")
                    .setMessage("页眉页脚检查异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
}
