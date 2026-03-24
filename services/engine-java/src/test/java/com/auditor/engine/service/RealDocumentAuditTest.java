package com.auditor.engine.service;

import com.auditor.grpc.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Windows 环境适配版：真实文档全面审计测试类
 * 修复了路径转义导致的 [ERROR] 非法逃逸符 错误
 */
public class RealDocumentAuditTest {

    private static final Logger logger = LoggerFactory.getLogger(RealDocumentAuditTest.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private KieContainer kieContainer;

    @BeforeEach
    public void setUp() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            kieContainer = kieServices.getKieClasspathContainer();
            logger.info("Drools 规则容器初始化成功，准备加载 56 条规则");
        } catch (Exception e) {
            logger.error("Drools 初始化失败，请检查规则路径或依赖: {}", e.getMessage());
        }
    }

    @Test
    public void testFullDocumentAudit() throws IOException {
        // --- 修复点 1: 使用正斜杠 '/' 替换反斜杠 '\'，避免转义错误 ---
        // 跨平台路径：从 classpath 加载真实测试数据文件
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("data/audit_results_final.json");
        String jsonPath = resourceUrl != null ? resourceUrl.getFile() : 
            "src/test/resources/data/audit_results_final.json";
        
        File jsonFile = new File(jsonPath);
        if (!jsonFile.exists()) {
            logger.error("解析后的 JSON 文件不存在: {}", jsonPath);
            return;
        }

        JsonNode rootNode = objectMapper.readTree(jsonFile);
        ParsedData data = convertJsonToParsedData(rootNode);

        // 2. 收集所有规则检出的问题
        List<Issue> allIssues = new ArrayList<>();

        // 依次执行三个会话，覆盖 56 条规则
        auditWithSession("formattingSession", data, allIssues);
        auditWithSession("integritySession", data, allIssues);
        auditWithSession("referenceSession", data, allIssues);

        // 3. 构建严格对齐的 AuditResponse JSON 结构
        ObjectNode responseNode = buildAuditResponse(allIssues);
        
        // --- 修复点 2: 同样使用正斜杠，并将输出路径指向 target 目录 ---
        String outputPath = "target/audit_results_final.json";
        
        String outputJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseNode);
        
        // 确保输出目录存在
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        Files.write(Paths.get(outputPath), outputJson.getBytes(StandardCharsets.UTF_8));
        
        logger.info("全面审计完成，共检出 {} 个问题", allIssues.size());
        logger.info("报告已生成至: {}", outputPath);
        System.out.println(outputJson);
    }

    private void auditWithSession(String sessionName, ParsedData data, List<Issue> results) {
        if (kieContainer == null) return;
        KieSession session = null;
        try {
            session = kieContainer.newKieSession(sessionName);
            if (session == null) {
                logger.warn("无法创建会话: {}，请检查 kmodule.xml 配置", sessionName);
                return;
            }
            
            session.setGlobal("results", results);
            session.setGlobal("logger", logger);

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

    private ParsedData convertJsonToParsedData(JsonNode rootNode) {
        ParsedData.Builder builder = ParsedData.newBuilder()
                .setDocId(rootNode.path("doc_id").asText("unknown"));

        JsonNode meta = rootNode.get("metadata");
        if (meta != null) {
            builder.setMetadata(DocumentMetadata.newBuilder()
                    .setTitle(meta.path("title").asText(""))
                    .setPageCount(meta.path("total_pages").asInt(0))
                    .build());
        }

        JsonNode sections = rootNode.get("sections");
        if (sections != null && sections.isArray()) {
            for (JsonNode sn : sections) {
                Section.Builder sb = Section.newBuilder()
                        .setSectionId(sn.path("section_id").asInt())
                        .setType(sn.path("type").asText("paragraph"))
                        .setLevel(sn.path("level").asInt(0))
                        .setText(sn.path("text").asText(""));
                
                JsonNode props = sn.get("properties");
                if (props != null) {
                    props.fields().forEachRemaining(e -> sb.putProps(e.getKey(), e.getValue().asText()));
                }
                builder.addSections(sb.build());
            }
        }

        if (rootNode.has("references")) {
            for (JsonNode rn : rootNode.get("references")) {
                builder.addReferences(Reference.newBuilder()
                        .setRefId(rn.path("ref_id").asText(""))
                        .setRawText(rn.path("raw_text").asText(""))
                        .build());
            }
        }
        return builder.build();
    }

    private ObjectNode buildAuditResponse(List<Issue> issues) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode issuesArray = root.putArray("issues");
        float totalScoreImpact = 0;

        for (Issue issue : issues) {
            ObjectNode item = issuesArray.addObject();
            item.put("type", issue.getCode());
            item.put("description", issue.getMessage());
            item.put("location", "Section ID: " + issue.getSectionId());
            item.put("suggestion", issue.getSuggestion());
            item.put("severity", issue.getSeverity().name());
            
            float impact = calculateImpact(issue.getSeverity());
            item.put("scoreImpact", impact);
            item.put("timestamp", System.currentTimeMillis());
            item.put("module", getModuleName(issue.getCode()));
            
            totalScoreImpact += impact;
        }

        root.put("scoreImpact", totalScoreImpact);
        return root;
    }

    private float calculateImpact(Severity s) {
        switch(s) {
            case CRITICAL: return 10.0f;
            case HIGH: return 5.0f;
            case MEDIUM: return 2.0f;
            case LOW: return 1.0f;
            default: return 0.0f;
        }
    }

    private String getModuleName(String code) {
        if (code.startsWith("FMT")) return "FormattingAuditor";
        if (code.startsWith("ERR_INT")) return "DocumentIntegrityScan";
        if (code.startsWith("ERR_REF")) return "ReferenceConsistencyChecker";
        return "GeneralAuditor";
    }
}