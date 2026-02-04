package com.auditor.engine.service;

<<<<<<< HEAD
import com.auditor.grpc.ParsedData;
import com.auditor.grpc.AuditResponse;

public class ReferenceChecker {

    // 占位：执行引用闭环一致性检查，返回 AuditResponse（暂为空实现）
    public AuditResponse checkReferences(ParsedData data) {
        // TODO: 实现引用匹配与一致性检查
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

import java.util.List;
import java.util.ArrayList;

@Service
public class ReferenceChecker {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceChecker.class);
    private final KieContainer kieContainer;

    public ReferenceChecker() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            this.kieContainer = kieServices.getKieClasspathContainer();
            logger.info("引用检查规则引擎初始化成功");
        } catch (Exception e) {
            logger.error("规则引擎初始化失败", e);
            throw new RuntimeException("无法初始化规则引擎", e);
        }
    }

    public List<Issue> checkReferences(ParsedData data) {
        List<Issue> issues = new ArrayList<>();

        if (data == null) {
            logger.error("输入数据为空");
            issues.add(createSystemError("输入数据为空"));
            return issues;
        }

        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession("referenceSession");

            // 设置全局变量
            kieSession.setGlobal("results", issues);
            kieSession.setGlobal("logger", logger);

            // 插入事实数据
            kieSession.insert(data);

            // 插入所有章节
            for (Section section : data.getSectionsList()) {
                kieSession.insert(section);
            }

            // 插入所有参考文献
            for (Reference ref : data.getReferencesList()) {
                kieSession.insert(ref);
            }

            // 执行所有规则
            int firedRules = kieSession.fireAllRules();
            logger.info("引用检查完成，触发 {} 条规则，发现 {} 个问题",
                    firedRules, issues.size());

        } catch (Exception e) {
            logger.error("引用检查执行异常", e);
            issues.add(createSystemError("引用检查引擎异常: " + e.getMessage()));
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
                logger.debug("KieSession 资源已释放");
            }
        }

        return issues;
    }

    private Issue createSystemError(String message) {
        return Issue.newBuilder()
                .setCode("ERR_REF_ENGINE")
                .setMessage(message)
                .setSeverity(Severity.HIGH)
                .setSuggestion("请检查系统日志或联系技术支持")
                .setOriginalSnippet("系统错误")
                .build();
    }
}
>>>>>>> main
