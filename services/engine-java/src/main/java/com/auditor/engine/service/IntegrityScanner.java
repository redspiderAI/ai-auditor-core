package com.auditor.engine.service;

<<<<<<< HEAD
import com.auditor.grpc.ParsedData;
import com.auditor.grpc.AuditResponse;

public class IntegrityScanner {

    // 占位：检查文档必备章节并返回 AuditResponse
    public AuditResponse scanIntegrity(ParsedData data) {
        // TODO: 实现状态机与章节完整性检查
        return AuditResponse.newBuilder().setScoreImpact(0.0f).build();
    }
}
=======
import com.auditor.grpc.*;
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

    private static final Logger logger = LoggerFactory.getLogger(IntegrityScanner.class);
    private final KieContainer kieContainer;

    public IntegrityScanner() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            this.kieContainer = kieServices.getKieClasspathContainer();
            logger.info("完整性检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.error("规则引擎初始化失败", e);
            throw new RuntimeException("无法初始化规则引擎", e);
        }
    }

    public List<Issue> scanIntegrity(ParsedData data) {
        List<Issue> issues = new ArrayList<>();

        // 输入校验
        if (data == null || !data.hasMetadata()) {
            logger.warn("输入数据为空或缺少元数据");
            issues.add(createErrorIssue("ERR_INTEGRITY_NULL",
                    "无法识别文档类型：输入数据为空或缺少元数据", 0, Severity.CRITICAL));
            return issues;
        }

        logger.info("开始完整性检查，文档ID: {}, 标题: {}",
                data.getDocId(), data.getMetadata().getTitle());

        KieSession kieSession = null;

        try {
            // 创建 KieSession
            kieSession = kieContainer.newKieSession("integritySession");

            // 设置全局结果收集器
            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            // 插入事实数据
            kieSession.insert(data);
            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            // 执行完整性检查规则
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
    public List<Issue> checkIntegrity(ParsedData data) {
        List<Issue> issues = new ArrayList<>();

        if (data == null || data.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }

        try {
            // 1. 检查章节编号连续性
            checkSectionNumbering(data, issues);

            // 2. 检查标题层级结构
            checkHeadingHierarchy(data, issues);

            // 3. 检查必备章节
            checkRequiredSections(data, issues);

            // 4. 检查图表编号连续性
            checkFigureTableNumbering(data, issues);

            // 5. 检查文档结构完整性
            checkDocumentStructure(data, issues);

        } catch (Exception e) {
            logger.error("章节完整性检查异常", e);
            issues.add(createErrorIssue("ERR_INTEGRITY_CHECK",
                    "章节完整性检查异常: " + e.getMessage(), 0, Severity.HIGH));
        }

        logger.info("章节完整性检查完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 检查章节编号连续性
     */
    private void checkSectionNumbering(ParsedData data, List<Issue> issues) {
        int expectedSectionId = 1;
        for (Section section : data.getSectionsList()) {
            if (section.getSectionId() != expectedSectionId) {
                Issue issue = createIntegrityIssue("ERR_INT_NUM_001",
                        "章节编号不连续: 应为 " + expectedSectionId + "，实际为 " + section.getSectionId(),
                        section.getSectionId(),
                        "重新排列章节编号",
                        section.getText());
                issues.add(issue);
            }
            expectedSectionId++;
        }
    }

    /**
     * 检查标题层级结构
     */
    private void checkHeadingHierarchy(ParsedData data, List<Issue> issues) {
        int lastLevel = 0;
        int lastSectionId = 0;

        for (Section section : data.getSectionsList()) {
            if ("heading".equals(section.getType())) {
                int currentLevel = section.getLevel();

                // 检查标题层级跳跃
                if (currentLevel > lastLevel + 1) {
                    Issue issue = createIntegrityIssue("ERR_INT_HIER_001",
                            "标题层级跳跃: 从 " + lastLevel + " 级跳至 " + currentLevel + " 级",
                            section.getSectionId(),
                            "应逐级增加标题层级",
                            section.getText());
                    issues.add(issue);
                }

                // 检查同级标题顺序
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

    /**
     * 检查必备章节
     */
    private void checkRequiredSections(ParsedData data, List<Issue> issues) {
        // 学术论文必备章节
        String[] requiredSections = { "摘要", "引言", "正文", "结论", "参考文献" };

        for (String requiredSection : requiredSections) {
            boolean found = data.getSectionsList().stream()
                    .anyMatch(section -> section.getText().contains(requiredSection));

            if (!found) {
                Issue issue = createIntegrityIssue("ERR_INT_REQ_001",
                        "缺少必备章节: " + requiredSection,
                        0, // 全局问题
                        "添加" + requiredSection + "章节",
                        "");
                issues.add(issue);
            }
        }
    }

    /**
     * 检查图表编号连续性
     */
    private void checkFigureTableNumbering(ParsedData data, List<Issue> issues) {
        int figureCount = 1;
        int tableCount = 1;

        for (Section section : data.getSectionsList()) {
            if ("figure".equals(section.getType())) {
                // 检查图表编号
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

    /**
     * 检查文档结构完整性
     */
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

        // 检查文档结构顺序
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

        // 检查必备结构元素
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

    /**
     * 创建完整性检查问题
     */
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

    /**
     * 创建错误问题
     */
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
>>>>>>> main
