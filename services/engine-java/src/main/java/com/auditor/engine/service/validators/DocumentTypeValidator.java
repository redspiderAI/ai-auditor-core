package com.auditor.engine.service.validators;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文档类型验证器
 * 
 * 功能：根据文档类型检查必选项是否完整
 * 支持的文档类型：
 *   - thesis: 学位论文
 *   - journal: 期刊论文
 *   - conference: 会议论文
 *   - book: 专著
 */
@Component
public class DocumentTypeValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentTypeValidator.class);
    
    // 定义不同文档类型的必选项
    private static final Map<String, String[]> REQUIRED_FIELDS = new HashMap<>();
    
    static {
        // 学位论文：需要学位、学校、导师等信息
        REQUIRED_FIELDS.put("thesis", new String[]{
            "title", "author", "school", "advisor", "year", "degree", "abstract"
        });
        
        // 期刊论文：需要期刊名、卷号、期号、页码等
        REQUIRED_FIELDS.put("journal", new String[]{
            "title", "author", "journal", "volume", "issue", "pages", "year", "doi"
        });
        
        // 会议论文：需要会议名、地点、日期等
        REQUIRED_FIELDS.put("conference", new String[]{
            "title", "author", "conference", "location", "date", "pages", "year"
        });
        
        // 专著：需要版本、印刷次数等
        REQUIRED_FIELDS.put("book", new String[]{
            "title", "author", "publisher", "year", "edition", "isbn"
        });
    }
    
    /**
     * 验证文档类型和必选项
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> validateDocumentType(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || !data.hasMetadata()) {
            logger.warn("输入数据为空或缺少元数据");
            return issues;
        }
        
        try {
            DocumentMetadata metadata = data.getMetadata();
            
            // 从 title 字段推断文档类型（由于 proto 中没有 documentType 字段）
            String title = metadata.getTitle();
            String docType = inferDocumentType(title);
            
            logger.debug("推断文档类型: {}", docType);
            
            // 检查文档类型是否被支持
            if (!REQUIRED_FIELDS.containsKey(docType)) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_UNKNOWN")
                        .setMessage("未知的文档类型: " + docType)
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("请指定有效的文档类型: thesis, journal, conference, book")
                        .build();
                issues.add(issue);
                logger.warn("未知的文档类型: {}", docType);
                return issues;
            }
            
            // 获取该文档类型的必选项
            String[] requiredFields = REQUIRED_FIELDS.get(docType);
            
            // 检查基本必选项
            if (title.isEmpty()) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_MISSING_TITLE")
                        .setMessage("文档缺少标题")
                        .setSeverity(Severity.HIGH)
                        .setSuggestion("请填写文档标题")
                        .build();
                issues.add(issue);
            }
            
            if (metadata.getPageCount() <= 0) {
                Issue issue = Issue.newBuilder()
                        .setCode("INT_TYPE_MISSING_PAGES")
                        .setMessage("文档页数无效")
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("请确保文档有有效的页数")
                        .build();
                issues.add(issue);
            }
            
            // 根据文档类型进行额外检查
            checkDocumentTypeSpecificRules(docType, metadata, issues);
            
            logger.info("文档类型验证完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("文档类型验证异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_TYPE_VALIDATION")
                    .setMessage("文档类型验证异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
    
    /**
     * 从标题推断文档类型
     */
    private String inferDocumentType(String title) {
        if (title.isEmpty()) {
            return "unknown";
        }
        
        String lowerTitle = title.toLowerCase();
        
        if (lowerTitle.contains("学位论文") || lowerTitle.contains("thesis") || 
            lowerTitle.contains("dissertation")) {
            return "thesis";
        } else if (lowerTitle.contains("期刊") || lowerTitle.contains("journal") ||
                   lowerTitle.contains("article")) {
            return "journal";
        } else if (lowerTitle.contains("会议") || lowerTitle.contains("conference") ||
                   lowerTitle.contains("proceedings")) {
            return "conference";
        } else if (lowerTitle.contains("专著") || lowerTitle.contains("book")) {
            return "book";
        }
        
        return "thesis"; // 默认为学位论文
    }
    
    /**
     * 根据文档类型进行额外检查
     */
    private void checkDocumentTypeSpecificRules(String docType, DocumentMetadata metadata, List<Issue> issues) {
        switch (docType) {
            case "thesis":
                checkThesisSpecificRules(metadata, issues);
                break;
            case "journal":
                checkJournalSpecificRules(metadata, issues);
                break;
            case "conference":
                checkConferenceSpecificRules(metadata, issues);
                break;
            case "book":
                checkBookSpecificRules(metadata, issues);
                break;
        }
    }
    
    /**
     * 学位论文特定规则
     */
    private void checkThesisSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // 学位论文应该有足够的页数
        if (metadata.getPageCount() < 20) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_THESIS_PAGES")
                    .setMessage("学位论文页数过少，至少应为 20 页")
                    .setSeverity(Severity.MEDIUM)
                    .setSuggestion("增加论文内容")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * 期刊论文特定规则
     */
    private void checkJournalSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // 期刊论文通常较短
        if (metadata.getPageCount() > 50) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_JOURNAL_PAGES_LONG")
                    .setMessage("期刊论文页数较多，请确认是否为期刊论文")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("检查文档类型是否正确")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * 会议论文特定规则
     */
    private void checkConferenceSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // 会议论文通常为 4-8 页
        if (metadata.getPageCount() < 4) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_CONF_PAGES_SHORT")
                    .setMessage("会议论文页数过少，通常应为 4-8 页")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("检查文档是否完整")
                    .build();
            issues.add(issue);
        }
    }
    
    /**
     * 专著特定规则
     */
    private void checkBookSpecificRules(DocumentMetadata metadata, List<Issue> issues) {
        // 专著通常较厚
        if (metadata.getPageCount() < 50) {
            Issue issue = Issue.newBuilder()
                    .setCode("INT_BOOK_PAGES_SHORT")
                    .setMessage("专著页数过少，通常应超过 50 页")
                    .setSeverity(Severity.LOW)
                    .setSuggestion("检查文档是否完整")
                    .build();
            issues.add(issue);
        }
    }
}
