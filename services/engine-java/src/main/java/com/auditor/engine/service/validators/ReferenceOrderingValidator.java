package com.auditor.engine.service.validators;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用排序校验器
 * 
 * 功能：检查参考文献是否按照正文中的出现顺序排列（顺序编码制）
 * 规则：
 *   1. 参考文献应按照正文中的引用顺序排列
 *   2. 参考文献编号应连续（不能跳号）
 * 严重程度：MEDIUM / HIGH
 */
@Component
public class ReferenceOrderingValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReferenceOrderingValidator.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)\\]");
    
    /**
     * 验证引用排序
     * 
     * @param data 解析后的文档数据
     * @return 发现的问题列表
     */
    public List<Issue> validateReferenceOrdering(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null) {
            logger.warn("输入数据为空");
            return issues;
        }
        
        try {
            // 1. 按照正文出现顺序收集引用
            List<Integer> citationOrder = extractCitationOrder(data);
            logger.debug("正文中的引用顺序: {}", citationOrder);
            
            // 2. 从参考文献中提取引用顺序
            List<Integer> referenceOrder = extractReferenceOrder(data);
            logger.debug("参考文献中的引用顺序: {}", referenceOrder);
            
            // 3. 检查是否按照引用顺序排列
            checkOrderingConsistency(citationOrder, referenceOrder, issues);
            
            // 4. 检查编号连续性
            checkNumberingContinuity(referenceOrder, issues);
            
            logger.info("引用排序验证完成，发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            logger.error("引用排序验证异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_REFERENCE_ORDER")
                    .setMessage("引用排序验证异常: " + e.getMessage())
                    .setSeverity(Severity.HIGH)
                    .build();
            issues.add(errorIssue);
        }
        
        return issues;
    }
    
    /**
     * 按照正文出现顺序提取引用
     */
    private List<Integer> extractCitationOrder(ParsedData data) {
        List<Integer> citationOrder = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        
        for (Section section : data.getSectionsList()) {
            Matcher matcher = CITATION_PATTERN.matcher(section.getText());
            while (matcher.find()) {
                try {
                    int id = Integer.parseInt(matcher.group(1));
                    if (!seen.contains(id)) {
                        citationOrder.add(id);
                        seen.add(id);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("无法解析引用 ID: {}", matcher.group(1));
                }
            }
        }
        
        return citationOrder;
    }
    
    /**
     * 从参考文献中提取引用顺序
     */
    private List<Integer> extractReferenceOrder(ParsedData data) {
        List<Integer> referenceOrder = new ArrayList<>();
        
        for (Reference ref : data.getReferencesList()) {
            String refId = ref.getRefId(); // "[1]"
            try {
                int id = Integer.parseInt(refId.replaceAll("[\\[\\]]", ""));
                referenceOrder.add(id);
            } catch (NumberFormatException e) {
                logger.warn("无法解析参考文献 ID: {}", refId);
            }
        }
        
        return referenceOrder;
    }
    
    /**
     * 检查排序一致性
     */
    private void checkOrderingConsistency(List<Integer> citationOrder, List<Integer> referenceOrder, List<Issue> issues) {
        if (citationOrder.isEmpty() || referenceOrder.isEmpty()) {
            return;
        }
        
        // 只比较存在的引用
        List<Integer> expectedOrder = new ArrayList<>();
        for (Integer id : citationOrder) {
            if (referenceOrder.contains(id)) {
                expectedOrder.add(id);
            }
        }
        
        // 从参考文献中提取对应的顺序
        List<Integer> actualOrder = new ArrayList<>();
        for (Integer id : referenceOrder) {
            if (expectedOrder.contains(id)) {
                actualOrder.add(id);
            }
        }
        
        if (!expectedOrder.equals(actualOrder)) {
            Issue issue = Issue.newBuilder()
                    .setCode("REF_ORDER_001")
                    .setMessage("参考文献顺序应与正文引用顺序一致")
                    .setSeverity(Severity.MEDIUM)
                    .setSuggestion("重新排列参考文献，使其与正文中的引用顺序一致")
                    .setOriginalSnippet("期望顺序: " + expectedOrder + ", 实际顺序: " + actualOrder)
                    .build();
            issues.add(issue);
            logger.debug("发现排序不一致");
        }
    }
    
    /**
     * 检查编号连续性
     */
    private void checkNumberingContinuity(List<Integer> referenceOrder, List<Issue> issues) {
        if (referenceOrder.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < referenceOrder.size(); i++) {
            int expectedId = i + 1;
            int actualId = referenceOrder.get(i);
            
            if (actualId != expectedId) {
                Issue issue = Issue.newBuilder()
                        .setCode("REF_ORDER_002")
                        .setMessage("参考文献编号不连续: 期望 [" + expectedId + "]，实际 [" + actualId + "]")
                        .setSeverity(Severity.HIGH)
                        .setSuggestion("重新编号参考文献，确保编号从 1 开始连续")
                        .build();
                issues.add(issue);
                logger.debug("发现编号不连续: 期望 [{}]，实际 [{}]", expectedId, actualId);
                break; // 只报告第一个不连续的位置
            }
        }
    }
}
