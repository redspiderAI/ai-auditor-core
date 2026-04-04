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
 * Member B (Java): gRPC service implementation for executing formatting and rule audits
 */
@Service
public class DocumentAuditorServiceImpl extends com.auditor.grpc.DocumentAuditorGrpc.DocumentAuditorImplBase {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAuditorServiceImpl.class);
    private final KieContainer kieContainer;

    /** section pre-filter service (stop detection + whitelist) */
    private final SectionFilterService sectionFilterService = new SectionFilterService();

    public DocumentAuditorServiceImpl() {
        KieServices kieServices = KieServices.Factory.get();
        this.kieContainer = kieServices.getKieClasspathContainer();
        logger.info("Drools rule engine loaded, gRPC audit service ready");
    }

    @Override
    public void auditRules(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        ParsedData data = request.getData();
        String targetRuleSet = request.getTargetRuleSet();
        logger.info("Received audit request, Document ID: {}, Target rule set: {}", data.getDocId(), targetRuleSet);

        List<Issue> allIssues = new ArrayList<>();

        try {
            // ── Pre-filter: truncate "Thesis dataset" and subsequent sections ──
            ParsedData filteredData = sectionFilterService.filterSections(data);
            logger.info("gRPC entry section pre-filter: original {} → filtered {}",
                    data.getSectionsCount(), filteredData.getSectionsCount());

            // 1. Execute formatting rules (formattingSession)
            auditWithSession("formattingSession", filteredData, allIssues);

            // 2. Execute integrity rules (integritySession)
            auditWithSession("integritySession", filteredData, allIssues);

            // 3. Execute reference rules (referenceSession)
            auditWithSession("referenceSession", filteredData, allIssues);

            // Build response
            AuditResponse response = AuditResponse.newBuilder()
                    .addAllIssues(allIssues)
                    .setScoreImpact(calculateTotalScore(allIssues))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            logger.info("Audit completed, total issues found: {}", allIssues.size());

        } catch (Exception e) {
            logger.error("Audit execution error: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Audit execution error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private void auditWithSession(String sessionName, ParsedData data, List<Issue> results) {
        KieSession session = null;
        try {
            session = kieContainer.newKieSession(sessionName);
            if (session == null) {
                logger.warn("Unable to create session: {}, please check kmodule.xml configuration", sessionName);
                return;
            }
            session.setGlobal("results", results);
            session.setGlobal("logger", logger);

            // Insert data objects to trigger DRL rules
            session.insert(data);
            for (Section s : data.getSectionsList()) session.insert(s);
            for (Reference r : data.getReferencesList()) session.insert(r);
            if (data.hasMetadata()) session.insert(data.getMetadata());

            int fired = session.fireAllRules();
            logger.info("Session [{}] executed, fired {} rules", sessionName, fired);
        } catch (Exception e) {
            logger.error("Session [{}] execution error: {}", sessionName, e.getMessage());
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