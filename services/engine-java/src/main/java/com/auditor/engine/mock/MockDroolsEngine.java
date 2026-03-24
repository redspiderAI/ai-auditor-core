package com.auditor.engine.mock;

import com.auditor.grpc.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 模拟 Drools 引擎实现
 * 用于在 Drools 无法初始化时提供基本的规则检查功能
 */
public class MockDroolsEngine {
    
    /**
     * 检查排版规则
     */
    public static List<Issue> checkFormattingRules(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || data.getSectionsList().isEmpty()) {
            return issues;
        }
        
        // 规则 1: 一级标题必须使用黑体
        for (Section section : data.getSectionsList()) {
            if ("heading".equals(section.getType()) && section.getLevel() == 1) {
                String fontFamily = section.getPropsMap().get("font-family");
                if (fontFamily == null || (!fontFamily.contains("黑体") && !fontFamily.contains("SimHei") && !fontFamily.equals("黑体"))) {
                    issues.add(Issue.newBuilder()
                            .setCode("ERR_FONT_001")
                            .setMessage("一级标题必须使用黑体")
                            .setSectionId(section.getSectionId())
                            .setSeverity(Severity.MEDIUM)
                            .build());
                }
            }
            
            // 规则 2: 一级标题字号应该在 16-18pt
            if ("heading".equals(section.getType()) && section.getLevel() == 1) {
                String fontSize = section.getPropsMap().get("font-size");
                if (fontSize != null) {
                    try {
                        int size = Integer.parseInt(fontSize);
                        if (size < 14 || size > 20) {
                            issues.add(Issue.newBuilder()
                                    .setCode("ERR_SIZE_001")
                                    .setMessage("一级标题字号应该在 14-20pt")
                                    .setSectionId(section.getSectionId())
                                    .setSeverity(Severity.MEDIUM)
                                    .build());
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的字号
                    }
                }
            }
            
            // 规则 3: 二级标题字号应该在 14-16pt
            if ("heading".equals(section.getType()) && section.getLevel() == 2) {
                String fontSize = section.getPropsMap().get("font-size");
                if (fontSize != null) {
                    try {
                        int size = Integer.parseInt(fontSize);
                        if (size < 12 || size > 18) {
                            issues.add(Issue.newBuilder()
                                    .setCode("ERR_SIZE_002")
                                    .setMessage("二级标题字号应该在 12-18pt")
                                    .setSectionId(section.getSectionId())
                                    .setSeverity(Severity.LOW)
                                    .build());
                        }
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
            
            // 规则 4: 正文字号应该是 12pt
            if ("paragraph".equals(section.getType())) {
                String fontSize = section.getPropsMap().get("font-size");
                if (fontSize != null) {
                    try {
                        int size = Integer.parseInt(fontSize);
                        if (size != 12) {
                            issues.add(Issue.newBuilder()
                                    .setCode("ERR_SIZE_003")
                                    .setMessage("正文字号应该是 12pt")
                                    .setSectionId(section.getSectionId())
                                    .setSeverity(Severity.LOW)
                                    .build());
                        }
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
            
            // 规则 9: 检查行距 - 修复逻辑，行距应该 >= 1.5
            String lineHeight = section.getPropsMap().get("line-height");
            if (lineHeight != null && !lineHeight.isEmpty()) {
                try {
                    float lineHeightValue = Float.parseFloat(lineHeight);
                    // 行距应该不小于 1.5，如果小于 1.5 就是问题
                    if (lineHeightValue < 1.5) {
                        issues.add(Issue.newBuilder()
                                .setCode("FMT_LINE_SPACING_001")
                                .setMessage("行距应不小于1.5倍")
                                .setSectionId(section.getSectionId())
                                .setSeverity(Severity.LOW)
                                .build());
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的行距
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 检查参考文献规则
     */
    public static List<Issue> checkReferenceRules(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null) {
            return issues;
        }
        
        // 提取正文中的所有引用
        Set<String> citedReferences = new HashSet<>();
        Pattern refPattern = Pattern.compile("\\[(\\d+)\\]");
        
        for (Section section : data.getSectionsList()) {
            if ("paragraph".equals(section.getType())) {
                var matcher = refPattern.matcher(section.getText());
                while (matcher.find()) {
                    citedReferences.add("[" + matcher.group(1) + "]");
                }
            }
        }
        
        // 获取参考文献列表
        Set<String> definedReferences = new HashSet<>();
        for (Reference ref : data.getReferencesList()) {
            definedReferences.add(ref.getRefId());
        }
        
        // 规则 1: 检查缺失的参考文献
        for (String cited : citedReferences) {
            if (!definedReferences.contains(cited)) {
                issues.add(Issue.newBuilder()
                        .setCode("ERR_REF_MISSING")
                        .setMessage("参考文献 " + cited + " 在文末未定义")
                        .setSeverity(Severity.CRITICAL)
                        .build());
            }
        }
        
        // 规则 2: 检查未使用的参考文献
        for (String defined : definedReferences) {
            if (!citedReferences.contains(defined)) {
                issues.add(Issue.newBuilder()
                        .setCode("ERR_REF_UNUSED")
                        .setMessage("参考文献 " + defined + " 在正文中未被引用")
                        .setSeverity(Severity.LOW)
                        .build());
            }
        }
        
        return issues;
    }
    
    /**
     * 检查完整性规则
     */
    public static List<Issue> checkIntegrityRules(ParsedData data) {
        List<Issue> issues = new ArrayList<>();
        
        if (data == null || data.getSectionsList().isEmpty()) {
            return issues;
        }
        
        // 提取所有章节标题
        Set<String> chapters = new HashSet<>();
        int prevLevel = 0;
        Set<Integer> seenIds = new HashSet<>();
        
        for (Section section : data.getSectionsList()) {
            // 规则 1: 检查重复的 section ID
            if (seenIds.contains(section.getSectionId())) {
                issues.add(Issue.newBuilder()
                        .setCode("ERR_INTEGRITY_DUPLICATE")
                        .setMessage("重复的 section ID: " + section.getSectionId())
                        .setSectionId(section.getSectionId())
                        .setSeverity(Severity.CRITICAL)
                        .build());
            }
            seenIds.add(section.getSectionId());
            
            if ("heading".equals(section.getType())) {
                chapters.add(section.getText());
                
                // 规则 2: 检查标题层级跳跃
                if (prevLevel > 0 && section.getLevel() > prevLevel + 1) {
                    issues.add(Issue.newBuilder()
                            .setCode("ERR_INTEGRITY_HIER")
                            .setMessage("标题层级跳跃: 从 " + prevLevel + " 级跳到 " + section.getLevel() + " 级")
                            .setSectionId(section.getSectionId())
                            .setSeverity(Severity.MEDIUM)
                            .build());
                }
                prevLevel = section.getLevel();
            }
        }
        
        // 规则 3: 检查必备章节
        String[] requiredChapters = {"摘要", "引言", "正文", "结论", "参考文献"};
        int foundCount = 0;
        for (String required : requiredChapters) {
            boolean found = false;
            for (String chapter : chapters) {
                if (chapter.contains(required)) {
                    found = true;
                    foundCount++;
                    break;
                }
            }
            if (!found) {
                issues.add(Issue.newBuilder()
                        .setCode("ERR_INTEGRITY_REQ_" + required)
                        .setMessage("缺失必备章节: " + required)
                        .setSeverity(Severity.CRITICAL)
                        .build());
            }
        }
        
        return issues;
    }
}
