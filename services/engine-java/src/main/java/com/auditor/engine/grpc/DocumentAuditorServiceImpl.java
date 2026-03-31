package com.auditor.engine.grpc;

import com.auditor.engine.service.SectionFilterService;
import com.auditor.grpc.*;
import io.grpc.stub.StreamObserver;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 成员 B (Java): 执行格式与规则审查的 gRPC 服务实现
 */
@Service
public class DocumentAuditorServiceImpl extends com.auditor.grpc.DocumentAuditorGrpc.DocumentAuditorImplBase {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAuditorServiceImpl.class);
    private final KieContainer kieContainer;

    /** section 预过滤服务（停止检测 + 白名单） */
    private final SectionFilterService sectionFilterService = new SectionFilterService();

    public DocumentAuditorServiceImpl() {
        KieServices kieServices = KieServices.Factory.get();
        this.kieContainer = kieServices.getKieClasspathContainer();
        logger.info("Drools 规则引擎已加载，gRPC 审计服务准备就绪");
    }

    @Override
    public void auditRules(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        ParsedData data = request.getData();
        String targetRuleSet = request.getTargetRuleSet();
        logger.info("收到审计请求，文档ID: {}, 目标规则集: {}", data.getDocId(), targetRuleSet);

        List<Issue> allIssues = new ArrayList<>();

        try {
            // ── 预过滤：截断「学位论文数据集」及后续 sections ──
            ParsedData filteredData = sectionFilterService.filterSections(data);
            logger.info("gRPC 入口 section 预过滤：原始 {} 个 → 过滤后 {} 个",
                    data.getSectionsCount(), filteredData.getSectionsCount());

            // 1. 执行排版规则 (formattingSession)
            auditWithSession("formattingSession", filteredData, allIssues);

            // 2. 执行完整性规则 (integritySession)
            auditWithSession("integritySession", filteredData, allIssues);

            // 3. 执行参考文献规则 (referenceSession)
            auditWithSession("referenceSession", filteredData, allIssues);

            // 构建响应
            AuditResponse response = AuditResponse.newBuilder()
                    .addAllIssues(allIssues)
                    .setScoreImpact(calculateTotalScore(allIssues))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            logger.info("审计完成，共检出 {} 个问题", allIssues.size());

        } catch (Exception e) {
            logger.error("审计执行异常: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("审计执行异常: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private void auditWithSession(String sessionName, ParsedData data, List<Issue> results) {
        KieSession session = null;
        try {
            session = kieContainer.newKieSession(sessionName);
            if (session == null) {
                logger.warn("无法创建会话: {}，请检查 kmodule.xml 配置", sessionName);
                return;
            }
            session.setGlobal("results", results);
            session.setGlobal("logger", logger);

            // 插入数据对象以触发 DRL 规则
            session.insert(data);
            for (Section s : data.getSectionsList()) session.insert(s);
            for (Reference r : data.getReferencesList()) session.insert(r);
            if (data.hasMetadata()) session.insert(data.getMetadata());

            int fired = session.fireAllRules();
            logger.info("会话 [{}] 执行完成，触发 {} 条规则", sessionName, fired);
        } catch (Exception e) {
            logger.error("会话 [{}] 执行异常: {}", sessionName, e.getMessage());
        } finally {
            if (session != null) session.dispose();
        }
    }

    private float calculateTotalScore(List<Issue> issues) {
        float total = 0;
        for (Issue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL: total += 10.0f; break;
                case HIGH: total += 5.0f; break;
                case MEDIUM: total += 2.0f; break;
                case LOW: total += 1.0f; break;
                default: break;
            }
        }
        return total;
    }
}