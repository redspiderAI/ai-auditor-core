package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.List;
import java.util.ArrayList;

/**
 * 参考文献检查服务
 * 所有检查逻辑都由 Drools 规则引擎处理
 * 本类只负责调用 Drools 引擎，不包含任何硬编码的判断逻辑
 */
@Service
public class ReferenceChecker {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceChecker.class);
    private KieContainer kieContainer;

    /** section 预过滤服务（停止检测 + 白名单） */
    private final SectionFilterService sectionFilterService = new SectionFilterService();

    public ReferenceChecker() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            kieContainer = kieServices.getKieClasspathContainer();
            logger.info("参考文献检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.error("Drools 规则引擎初始化失败: {}", e.getMessage());
            kieContainer = null;
        }
    }

    /**
     * 检查参考文献
     * 所有检查逻辑都由 reference.drl 中的规则处理
     */
    public List<Issue> checkReferences(ParsedData rawData) {
        List<Issue> issues = new ArrayList<>();

        if (rawData == null) {
            logger.error("输入数据为空");
            return issues;
        }

        if (kieContainer == null) {
            logger.error("Drools 规则引擎未初始化");
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_ENGINE_INIT")
                    .setMessage("参考文献检查引擎未初始化")
                    .setSeverity(Severity.CRITICAL)
                    .build();
            issues.add(errorIssue);
            return issues;
        }

        // ── 预过滤：截断「学位论文数据集」及后续 sections ──
        ParsedData data = sectionFilterService.filterSections(rawData);
        logger.info("参考文献检查 section 预过滤：原始 {} 个 → 过滤后 {} 个",
                rawData.getSectionsCount(), data.getSectionsCount());

        // 使用 Drools 进行所有参考文献检查
        return checkReferencesWithDrools(data, issues);
    }

    /**
     * 使用 Drools 引擎进行参考文献检查
     * 所有判断逻辑都在 reference.drl 中定义
     */
    private List<Issue> checkReferencesWithDrools(ParsedData data, List<Issue> issues) {
        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession("referenceSession");

            // 设置全局变量
            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            // 插入数据对象
            kieSession.insert(data);

            // 插入所有章节（用于检查引用一致性）
            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            // 插入所有参考文献（用于检查格式）
            for (Reference ref : data.getReferencesList()) {
                kieSession.insert(ref);
            }

            // 触发所有规则
            int firedRules = kieSession.fireAllRules();
            logger.info("参考文献检查完成，触发 {} 条规则，发现 {} 个问题",
                    firedRules, issues.size());

        } catch (Exception e) {
            logger.error("参考文献检查执行异常", e);
            Issue errorIssue = Issue.newBuilder()
                    .setCode("ERR_REF_CHECK_EXCEPTION")
                    .setMessage("参考文献检查异常: " + e.getMessage())
                    .setSeverity(Severity.CRITICAL)
                    .build();
            issues.add(errorIssue);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
                logger.debug("参考文献检查 KieSession 已释放");
            }
        }

        return issues;
    }
}
