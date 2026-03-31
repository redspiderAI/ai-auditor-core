package com.auditor.engine.service;

import com.auditor.grpc.*;
import com.auditor.engine.mock.MockDroolsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.ArrayList;
import java.util.List;

@Service
public class IntegrityScanner {

    /** section 预过滤服务（停止检测 + 白名单） */
    private final SectionFilterService sectionFilterService = new SectionFilterService();

    private static final Logger logger = LoggerFactory.getLogger(IntegrityScanner.class);
    private KieContainer kieContainer;

    public IntegrityScanner() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            kieContainer = kieServices.getKieClasspathContainer();
            logger.info("完整性检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.warn("Drools 规则引擎初始化失败，将使用模拟引擎: {}", e.getMessage());
            kieContainer = null;
        }
    }

    public List<Issue> scanIntegrity(ParsedData rawData) {
        List<Issue> issues = new ArrayList<>();

        if (rawData == null || !rawData.hasMetadata()) {
            logger.warn("输入数据为空或缺少元数据");
            issues.add(createErrorIssue("ERR_INTEGRITY_NULL",
                    "无法识别文档类型：输入数据为空或缺少元数据", 0, Severity.CRITICAL));
            return issues;
        }

        // ── 预过滤：截断「学位论文数据集」及后续 sections ──
        ParsedData data = sectionFilterService.filterSections(rawData);
        logger.info("完整性检查 section 预过滤：原始 {} 个 → 过滤后 {} 个",
                rawData.getSectionsCount(), data.getSectionsCount());

        logger.info("开始完整性检查，文档ID: {}, 标题: {}",
                data.getDocId(), data.getMetadata().getTitle());

        // 如果 Drools 可用，使用 Drools；否则使用模拟引擎
        if (kieContainer != null) {
            return scanIntegrityWithDrools(data, issues);
        } else {
            logger.info("使用模拟 Drools 引擎进行完整性检查");
            return MockDroolsEngine.checkIntegrityRules(data);
        }
    }

    private List<Issue> scanIntegrityWithDrools(ParsedData data, List<Issue> issues) {
        KieSession kieSession = null;

        try {
            kieSession = kieContainer.newKieSession("integritySession");

            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            kieSession.insert(data);
            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            int firedRules = kieSession.fireAllRules();
            logger.info("完整性检查完成，触发 {} 条规则，发现 {} 个问题",
                    firedRules, issues.size());

        } catch (Exception e) {
            logger.error("完整性检查执行异常", e);
            issues.add(createErrorIssue("ERR_INTEGRITY_ENGINE",
                    "完整性检查引擎异常: " + e.getMessage(), 0, Severity.HIGH));
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }

        return issues;
    }

    /**
     * 检查章节完整性
     */
    public List<Issue> checkIntegrity(ParsedData rawData) {
        List<Issue> issues = new ArrayList<>();

        if (rawData == null || rawData.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }

        // ── 预过滤 ──
        ParsedData data = sectionFilterService.filterSections(rawData);
        logger.info("章节完整性检查 section 预过滤：原始 {} 个 → 过滤后 {} 个",
                rawData.getSectionsCount(), data.getSectionsCount());

        try {
            checkSectionNumbering(data, issues);
            checkHeadingHierarchy(data, issues);
            checkRequiredSections(data, issues);
            checkFigureTableNumbering(data, issues);
            checkDocumentStructure(data, issues);

        } catch (Exception e) {
            logger.error("章节完整性检查异常", e);
            issues.add(createErrorIssue("ERR_INTEGRITY_CHECK",
                    "章节完整性检查异常: " + e.getMessage(), 0, Severity.HIGH));
        }

        logger.info("章节完整性检查完成，发现 {} 个问题", issues.size());
        return issues;
    }

    private void checkSectionNumbering(ParsedData data, List<Issue> issues) {
        // section_id 是 Rust 解析器分配的内部 ID，允许跳号（如目录页占用部分 ID），
        // 只要求 section_id 单调递增（不允许乱序或重复），不要求严格连续。
        int lastSectionId = -1;
        for (Section section : data.getSectionsList()) {
            int currentId = section.getSectionId();
            if (currentId <= lastSectionId) {
                Issue issue = createIntegrityIssue("ERR_INT_NUM_001",
                        "章节 ID 乱序或重复: 前一个 ID 为 " + lastSectionId + "，当前 ID 为 " + currentId,
                        currentId,
                        "检查文档解析结果，确保章节顺序正确",
                        section.getText());
                issues.add(issue);
            }
            lastSectionId = currentId;
        }
    }

    private void checkHeadingHierarchy(ParsedData data, List<Issue> issues) {
        int lastLevel = 0;
        int lastSectionId = 0;

        for (Section section : data.getSectionsList()) {
            if ("heading".equals(section.getType())) {
                int currentLevel = section.getLevel();

                if (currentLevel > lastLevel + 1) {
                    Issue issue = createIntegrityIssue("ERR_INT_HIER_001",
                            "标题层级跳跃: 从 " + lastLevel + " 级跳至 " + currentLevel + " 级",
                            section.getSectionId(),
                            "应逐级增加标题层级",
                            section.getText());
                    issues.add(issue);
                }

                if (currentLevel == lastLevel && section.getSectionId() <= lastSectionId) {
                    Issue issue = createIntegrityIssue("ERR_INT_HIER_002",
                            "同级标题顺序错误: 标题顺序混乱",
                            section.getSectionId(),
                            "调整同级标题的顺序",
                            section.getText());
                    issues.add(issue);
                }

                lastLevel = currentLevel;
                lastSectionId = section.getSectionId();
            }
        }
    }

    private void checkRequiredSections(ParsedData data, List<Issue> issues) {
        String[] requiredSections = { "摘要", "引言", "正文", "结论", "参考文献" };

        for (String requiredSection : requiredSections) {
            boolean found = data.getSectionsList().stream()
                    .anyMatch(section -> section.getText().contains(requiredSection));

            if (!found) {
                Issue issue = createIntegrityIssue("ERR_INT_REQ_001",
                        "缺少必备章节: " + requiredSection,
                        0,
                        "添加" + requiredSection + "章节",
                        "");
                issues.add(issue);
            }
        }
    }

    private void checkFigureTableNumbering(ParsedData data, List<Issue> issues) {
        int figureCount = 1;
        int tableCount = 1;

        for (Section section : data.getSectionsList()) {
            if ("figure".equals(section.getType())) {
                String sectionText = section.getText().toLowerCase();
                if (!sectionText.contains("图" + figureCount) &&
                        !sectionText.contains("figure " + figureCount)) {
                    Issue issue = createIntegrityIssue("ERR_INT_FIG_001",
                            "图表编号不连续: 应为第 " + figureCount + " 个图",
                            section.getSectionId(),
                            "按顺序重新编号图表",
                            section.getText());
                    issues.add(issue);
                }
                figureCount++;
            } else if ("table".equals(section.getType())) {
                String sectionText = section.getText().toLowerCase();
                if (!sectionText.contains("表" + tableCount) &&
                        !sectionText.contains("table " + tableCount)) {
                    Issue issue = createIntegrityIssue("ERR_INT_TAB_001",
                            "表格编号不连续: 应为第 " + tableCount + " 个表",
                            section.getSectionId(),
                            "按顺序重新编号表格",
                            section.getText());
                    issues.add(issue);
                }
                tableCount++;
            }
        }
    }

    private void checkDocumentStructure(ParsedData data, List<Issue> issues) {
        boolean hasAbstract = false;
        boolean hasConclusion = false;
        boolean hasReferences = false;

        for (Section section : data.getSectionsList()) {
            String text = section.getText().toLowerCase();
            if (text.contains("摘要") || text.contains("abstract")) {
                hasAbstract = true;
            }
            if (text.contains("结论") || text.contains("conclusion")) {
                hasConclusion = true;
            }
            if (text.contains("参考文献") || text.contains("references")) {
                hasReferences = true;
            }
        }

        if (hasAbstract && hasConclusion) {
            int abstractIndex = -1;
            int conclusionIndex = -1;

            for (int i = 0; i < data.getSectionsCount(); i++) {
                String text = data.getSections(i).getText().toLowerCase();
                if (text.contains("摘要") || text.contains("abstract")) {
                    abstractIndex = i;
                }
                if (text.contains("结论") || text.contains("conclusion")) {
                    conclusionIndex = i;
                }
            }

            if (abstractIndex > conclusionIndex) {
                Issue issue = createIntegrityIssue("ERR_INT_STRUCT_001",
                        "文档结构错误: 摘要应在结论之前",
                        0,
                        "调整章节顺序，确保摘要在结论之前",
                        "");
                issues.add(issue);
            }
        }

        if (!hasAbstract) {
            issues.add(createIntegrityIssue("ERR_INT_STRUCT_002",
                    "文档缺少摘要部分",
                    0,
                    "添加摘要章节",
                    ""));
        }

        if (!hasConclusion) {
            issues.add(createIntegrityIssue("ERR_INT_STRUCT_003",
                    "文档缺少结论部分",
                    0,
                    "添加结论章节",
                    ""));
        }

        if (!hasReferences) {
            issues.add(createIntegrityIssue("ERR_INT_STRUCT_004",
                    "文档缺少参考文献部分",
                    0,
                    "添加参考文献章节",
                    ""));
        }
    }

    private Issue createIntegrityIssue(String code, String message,
            int sectionId, String suggestion,
            String originalSnippet) {
        return Issue.newBuilder()
                .setCode(code)
                .setMessage(message)
                .setSectionId(sectionId)
                .setSeverity(Severity.MEDIUM)
                .setSuggestion(suggestion)
                .setOriginalSnippet(
                        originalSnippet.length() > 100 ? originalSnippet.substring(0, 100) + "..." : originalSnippet)
                .build();
    }

    private Issue createErrorIssue(String code, String message,
            int sectionId, Severity severity) {
        return Issue.newBuilder()
                .setCode(code)
                .setMessage(message)
                .setSectionId(sectionId)
                .setSeverity(severity)
                .setSuggestion("请检查文档或联系管理员")
                .setOriginalSnippet("")
                .build();
    }
}
