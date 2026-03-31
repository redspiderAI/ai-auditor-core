package com.auditor.engine.service;

import com.auditor.grpc.*;
import com.auditor.engine.mock.MockDroolsEngine;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
public class FormattingAuditor {

    private static final Logger logger = LoggerFactory.getLogger(FormattingAuditor.class);
    private KieContainer kieContainer;

    /** section 预过滤服务（停止检测 + 白名单） */
    private final SectionFilterService sectionFilterService = new SectionFilterService();

    public FormattingAuditor() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            kieContainer = kieServices.getKieClasspathContainer();
            logger.info("格式检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.warn("Drools 规则引擎初始化失败，将使用模拟引擎: {}", e.getMessage());
            kieContainer = null;
        }
    }

    public List<Issue> checkFormatting(ParsedData rawData) {
        List<Issue> issues = new ArrayList<>();

        if (rawData == null || rawData.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }

        // ── 预过滤：截断「学位论文数据集」及后续 sections ──
        ParsedData data = sectionFilterService.filterSections(rawData);
        logger.info("排版检查 section 预过滤：原始 {} 个 → 过滤后 {} 个",
                rawData.getSectionsCount(), data.getSectionsCount());

        // 如果 Drools 可用，使用 Drools；否则使用模拟引擎
        if (kieContainer != null) {
            return checkFormattingWithDrools(data, issues);
        } else {
            logger.info("使用模拟 Drools 引擎进行格式检查");
            return MockDroolsEngine.checkFormattingRules(data);
        }
    }
    
    private List<Issue> checkFormattingWithDrools(ParsedData data, List<Issue> issues) {
        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession("formattingSession");

            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            kieSession.insert(data);

            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            if (data.hasMetadata()) {
                kieSession.insert(data.getMetadata());
            }

            int firedRules = kieSession.fireAllRules();
            logger.info("格式检查完成，触发 {} 条规则，发现 {} 个问题",
                    firedRules, issues.size());

        } catch (Exception e) {
            logger.error("格式检查执行异常", e);

            Issue errorIssue = Issue.newBuilder()
                    .setCode("RULE_ENGINE_ERROR")
                    .setMessage("格式检查引擎异常: " + e.getMessage())
                    .setSeverity(Severity.CRITICAL)
                    .build();
            issues.add(errorIssue);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
                logger.debug("KieSession 资源已释放");
            }
        }

        return issues;
    }
}
