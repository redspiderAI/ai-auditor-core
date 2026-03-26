package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

// 实现如下
// 1. 初始化规则引擎
// 2. 加载格式检查规则,通过 kmodule.xml 配置
// 3. 对每个章节进行样式检查：
//    - 字体检查（标题、正文）
//    - 间距检查（行距、段距）
//    - 对齐方式检查
//    - 页面设置检查
// 4. 对每个违规项创建 Issue 对象
// 5. 返回所有发现的问题

@Service // 业务服务组件

public class FormattingAuditor {

    private static final Logger logger = LoggerFactory.getLogger(FormattingAuditor.class);
    private final KieContainer kieContainer;

    // 1. 初始化规则引擎
    public FormattingAuditor() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            this.kieContainer = kieServices.getKieClasspathContainer();
            logger.info("格式检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.error(" 规则引擎初始化失败", e);
            throw new RuntimeException("无法初始化规则引擎", e);
        }
    }

    // 接受 ParsedData，返回发现的问题列表（POJO Issue）
    public List<Issue> checkFormatting(ParsedData data) {
        // TODO: 集成 Drools 规则引擎或具体实现

        List<Issue> issues = new ArrayList<>();

        if (data == null || data.getSectionsCount() == 0) {
            logger.warn("输入数据为空或没有章节");
            return issues;
        }
        // 2. 加载格式检查规则并执行
        // try-finally 手动管理 KieSession 资源
        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession("formattingSession");

            // 设置全局结果收集器
            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            // 插入事实数据到规则引擎
            kieSession.insert(data);

            // 插入所有章节，供规则检查
            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            // 插入文档元数据
            if (data.hasMetadata()) {
                kieSession.insert(data.getMetadata());
            }

            // 执行所有格式检查规则
            int firedRules = kieSession.fireAllRules();
            logger.info("格式检查完成，触发 {} 条规则，发现 {} 个问题",
                    firedRules, issues.size());

        } catch (Exception e) {
            logger.error("格式检查执行异常", e);

            // 创建系统错误 Issue 对象
            Issue errorIssue = Issue.newBuilder()
                    .setCode("RULE_ENGINE_ERROR")
                    .setMessage("格式检查引擎异常: " + e.getMessage())
                    .setSeverity(Severity.CRITICAL)
                    .build();
            issues.add(errorIssue);
        } finally {
            // 关键：确保 KieSession 资源被正确释放
            if (kieSession != null) {
                kieSession.dispose();
                logger.debug("KieSession 资源已释放");
            }
        }

        // 构建并返回响应
        return issues;
    }
}
