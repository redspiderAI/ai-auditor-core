package com.auditor.engine.service.validators;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双向引用追溯器
 * 
 * 功能：检查正文引用与参考文献的双向映射关系
 * 规则：
 *   1. 正文中的所有引用都必须在参考文献中有对应条目
 *   2. 参考文献中的所有条目都应该被正文引用
 * 严重程度：HIGH / MEDIUM
 */
@Component
public class BidirectionalReferenceTracer {
    
    private static final Logger logger = LoggerFactory.getLogger(BidirectionalReferenceTracer.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)\\]");
    
    /**
     * 追溯双向引用关系
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> traceBidirectionalReferences(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null) {
            logger.warn("输入数据为空");
            return issues;
        }
        
        try {
            // 1. 收集正文中的所有引用
            Set<Integer> citedInText = extractCitationsFromText(data);
            logger.debug("正文中发现的引用: {}", citedInText);
            
            // 2. 收集参考文献中的所有引用 ID
            Set<Integer> referencedIds = extractReferencedIds(data);
            logger.debug("参考文献中的引用 ID: {}", referencedIds);
            
            // 3. 检查正文中的引用是否都在参考文献中
            checkMissingReferences(citedInText, referencedIds, issues);
            
            // 4. 检查参考文献中是否有未被引用的条目
            checkUnusedReferences(citedInText, referencedIds, issues);
            
            logger.info("双向引用追溯完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("双向引用追溯异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_REFERENCE_TRACE")
                    .setMessage("双向引用追溯异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
    
    /**
     * 从正文中提取所有引用
     */
    private Set<Integer> extractCitationsFromText(ParsedData data) {
        Set<Integer> citations = new HashSet<>();
        
        for (Section section : data.getSectionsList()) {
            Matcher matcher = CITATION_PATTERN.matcher(section.getText());
            while (matcher.find()) {
                try {
                    int citationId = Integer.parseInt(matcher.group(1));
                    citations.add(citationId);
                } catch (NumberFormatException e) {
                    logger.warn("无法解析引用 ID: {}", matcher.group(1));
                }
            }
        }
        
        return citations;
    }
    
    /**
     * 从参考文献中提取所有引用 ID
     */
    private Set<Integer> extractReferencedIds(ParsedData data) {
        Set<Integer> refIds = new HashSet<>();
        
        for (Reference ref : data.getReferencesList()) {
            String refId = ref.getRefId(); // "[1]"
            try {
                int id = Integer.parseInt(refId.replaceAll("[\\[\\]]", ""));
                refIds.add(id);
            } catch (NumberFormatException e) {
                logger.warn("无法解析参考文献 ID: {}", refId);
            }
        }
        
        return refIds;
    }
    
    /**
     * 检查缺失的引用（正文中有，参考文献中没有）
     */
    private void checkMissingReferences(Set<Integer> citedInText, Set<Integer> referencedIds, List<Issue> issues) {
        for (Integer citedId : citedInText) {
            if (!referencedIds.contains(citedId)) {
                Issue issue = Issue.newBuilder()
                        .setCode("REF_MISSING_001")
                        .setMessage("正文引用 [" + citedId + "] 在参考文献中未找到")
                        .setSeverity(Severity.HIGH)
                        .setSuggestion("在参考文献中添加 [" + citedId + "] 的条目，或删除正文中的引用")
                        .build();
                issues.add(issue);
                logger.debug("发现缺失的引用: [{}]", citedId);
            }
        }
    }
    
    /**
     * 检查未使用的引用（参考文献中有，正文中没有）
     */
    private void checkUnusedReferences(Set<Integer> citedInText, Set<Integer> referencedIds, List<Issue> issues) {
        for (Integer refId : referencedIds) {
            if (!citedInText.contains(refId)) {
                Issue issue = Issue.newBuilder()
                        .setCode("REF_UNUSED_001")
                        .setMessage("参考文献 [" + refId + "] 在正文中未被引用")
                        .setSeverity(Severity.MEDIUM)
                        .setSuggestion("删除未被引用的参考文献 [" + refId + "]，或在正文中添加引用")
                        .build();
                issues.add(issue);
                logger.debug("发现未使用的引用: [{}]", refId);
            }
        }
    }
}
