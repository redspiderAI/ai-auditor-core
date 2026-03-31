package com.auditor.engine.service;

import com.auditor.grpc.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SectionFilterService 专项测试
 *
 * <p>重要设计约束：停止检测只对 <b>type=heading</b> 的 section 生效，
 * type=paragraph 的目录条目（如「学位论文数据集\t25」）不触发截断。
 *
 * <p>测试覆盖：
 * <ol>
 *   <li>单元测试：停止关键词截断（heading 触发 / paragraph 不触发）、白名单跳过、null/空输入安全处理</li>
 *   <li>集成测试：基于真实 JSON 文件（李良循毕业论文）验证过滤效果</li>
 *   <li>端到端测试：过滤后的数据送入 IntegrityScanner / FormattingAuditor，
 *       验证「学位论文数据集」章节不产生误报</li>
 * </ol>
 */
public class SectionFilterServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(SectionFilterServiceTest.class);

    private SectionFilterService filterService;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        filterService = new SectionFilterService();
        objectMapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. 单元测试：matchesStopKeyword
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("停止关键词：包含「学位论文数据集」应命中")
    public void testMatchesStopKeyword_hit() {
        assertTrue(filterService.matchesStopKeyword("学位论文数据集"));
        assertTrue(filterService.matchesStopKeyword("学位论文数据集\t25"));   // 带制表符+页码
        assertTrue(filterService.matchesStopKeyword("  学位论文数据集  "));   // 带空格
    }

    @Test
    @DisplayName("停止关键词：普通正文不应命中")
    public void testMatchesStopKeyword_miss() {
        assertFalse(filterService.matchesStopKeyword("摘要"));
        assertFalse(filterService.matchesStopKeyword("参考文献"));
        assertFalse(filterService.matchesStopKeyword("结论"));
        assertFalse(filterService.matchesStopKeyword(null));
        assertFalse(filterService.matchesStopKeyword(""));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. 单元测试：isWhitelisted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("白名单：精确匹配应命中")
    public void testIsWhitelisted_hit() {
        assertTrue(filterService.isWhitelisted("学位论文数据集"));
        assertTrue(filterService.isWhitelisted("独创性声明"));
        assertTrue(filterService.isWhitelisted("学位论文版权使用授权书"));
        assertTrue(filterService.isWhitelisted("版权声明"));
    }

    @Test
    @DisplayName("白名单：非白名单文本不应命中")
    public void testIsWhitelisted_miss() {
        assertFalse(filterService.isWhitelisted("学位论文数据集\t25"));  // 带制表符不精确匹配
        assertFalse(filterService.isWhitelisted("摘要"));
        assertFalse(filterService.isWhitelisted(null));
        assertFalse(filterService.isWhitelisted(""));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. 单元测试：filterSections 截断逻辑
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("filterSections：null 输入安全返回 null")
    public void testFilterSections_null() {
        assertNull(filterService.filterSections(null));
    }

    @Test
    @DisplayName("filterSections：无关键词时全量保留")
    public void testFilterSections_noKeyword() {
        // 全部 paragraph，无关键词 → 全部保留
        ParsedData data = buildParsedData("摘要", "第一章 绪论", "第二章 方法", "结论", "参考文献");
        ParsedData result = filterService.filterSections(data);
        assertEquals(5, result.getSectionsCount(), "无关键词时所有 section 应保留");
    }

    @Test
    @DisplayName("filterSections：paragraph 类型的目录条目不触发截断")
    public void testFilterSections_paragraphNotTrigger() {
        // 目录条目是 paragraph 类型，即使包含关键词也不截断
        ParsedData data = buildParsedData("摘要", "第一章", "结论", "参考文献", "学位论文数据集\t25");
        // buildParsedData 默认 type=paragraph，所以「学位论文数据集\t25」不触发截断
        ParsedData result = filterService.filterSections(data);
        assertEquals(5, result.getSectionsCount(), "paragraph 类型目录条目不应触发截断，全部5个应保留");
    }

    @Test
    @DisplayName("filterSections：heading 类型的关键词在末尾，截断最后1个")
    public void testFilterSections_headingStopAtEnd() {
        // 最后一个是 heading 类型的「学位论文数据集」→ 触发截断
        ParsedData data = buildParsedDataMixed(
            new SectionSpec("摘要",       "paragraph"),
            new SectionSpec("第一章",     "paragraph"),
            new SectionSpec("结论",       "paragraph"),
            new SectionSpec("参考文献",   "paragraph"),
            new SectionSpec("学位论文数据集", "heading")   // ← heading，触发截断
        );
        ParsedData result = filterService.filterSections(data);
        assertEquals(4, result.getSectionsCount(), "heading 关键词在末尾，应截断1个");
        assertEquals("参考文献", result.getSections(3).getText());
    }

    @Test
    @DisplayName("filterSections：heading 类型的关键词在中间，截断后续所有")
    public void testFilterSections_headingStopInMiddle() {
        ParsedData data = buildParsedDataMixed(
            new SectionSpec("摘要",           "paragraph"),
            new SectionSpec("第一章",         "paragraph"),
            new SectionSpec("学位论文数据集", "heading"),   // ← heading，触发截断
            new SectionSpec("附录A",          "paragraph"),
            new SectionSpec("附录B",          "paragraph")
        );
        ParsedData result = filterService.filterSections(data);
        assertEquals(2, result.getSectionsCount(), "heading 关键词在中间，应截断后续3个");
    }

    @Test
    @DisplayName("filterSections：heading 类型的关键词在首位，结果为空")
    public void testFilterSections_headingStopAtBeginning() {
        ParsedData data = buildParsedDataMixed(
            new SectionSpec("学位论文数据集", "heading"),   // ← heading，触发截断
            new SectionSpec("摘要",           "paragraph"),
            new SectionSpec("结论",           "paragraph")
        );
        ParsedData result = filterService.filterSections(data);
        assertEquals(0, result.getSectionsCount(), "heading 关键词在首位，结果应为空");
    }

    @Test
    @DisplayName("filterSections：白名单 section 被静默跳过")
    public void testFilterSections_whitelistSkipped() {
        ParsedData data = buildParsedData("摘要", "独创性声明", "第一章", "版权声明", "结论");
        ParsedData result = filterService.filterSections(data);
        // 独创性声明 和 版权声明 被跳过，剩余 摘要、第一章、结论
        assertEquals(3, result.getSectionsCount(), "白名单 section 应被跳过");
        assertEquals("摘要", result.getSections(0).getText());
        assertEquals("第一章", result.getSections(1).getText());
        assertEquals("结论", result.getSections(2).getText());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. 集成测试：基于真实 JSON 文件
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("真实文档：李良循毕业论文 JSON — 过滤后不含「学位论文数据集」标题行及其表格内容")
    public void testRealDocument_jsonFilter() throws Exception {
        ParsedData rawData = loadRealDocumentJson();
        if (rawData == null) {
            logger.warn("跳过真实文档测试： JSON 文件不存在");
            return;
        }

        int originalCount = rawData.getSectionsCount();
        logger.info("原始 section 数量: {}", originalCount);

        // 验证 JSON 中确实包含表格行（type=table_row）
        long tableRowCount = rawData.getSectionsList().stream()
                .filter(s -> "table_row".equals(s.getType()))
                .count();
        logger.info("JSON 中共有 {} 个 table_row 类型 section", tableRowCount);
        assertTrue(tableRowCount > 0, "JSON 应包含学位论文数据集表格行（table_row），实际数量=" + tableRowCount);

        // 验证 JSON 中 section_id=64 是 paragraph 类型的目录条目（不应触发截断）
        boolean hasParagraphToc = rawData.getSectionsList().stream()
                .anyMatch(s -> s.getSectionId() == 64
                        && "paragraph".equals(s.getType())
                        && s.getText().contains("学位论文数据集"));
        assertTrue(hasParagraphToc, "section_id=64 应是 paragraph 类型的目录条目");

        // 验证 JSON 中最后一个 section 是 heading 类型的「学位论文数据集」（应触发截断）
        boolean hasHeadingStop = rawData.getSectionsList().stream()
                .anyMatch(s -> "heading".equals(s.getType())
                        && s.getText().contains("学位论文数据集"));
        assertTrue(hasHeadingStop, "JSON 中应存在 heading 类型的「学位论文数据集」章节标题");

        ParsedData filtered = filterService.filterSections(rawData);
        int filteredCount = filtered.getSectionsCount();
        logger.info("过滤后 section 数量: {}", filteredCount);

        // 验证：过滤后数量小于原始数量（截断了 heading 标题行 + 21 个表格行 = 22 个）
        assertTrue(filteredCount < originalCount,
                "过滤后 section 数量应小于原始数量，原始=" + originalCount + " 过滤后=" + filteredCount);

        // 验证：截断数量为 22（1个heading标题 + 21个table_row）
        int removedCount = originalCount - filteredCount;
        assertEquals(22, removedCount,
                "应截断 22 个 section（1个heading标题行 + 21个table_row），实际截断=" + removedCount);

        // 验证：过滤后不存在 heading 类型的「学位论文数据集」
        boolean hasStopHeading = filtered.getSectionsList().stream()
                .anyMatch(s -> "heading".equals(s.getType())
                        && filterService.matchesStopKeyword(s.getText()));
        assertFalse(hasStopHeading, "过滤后不应存在 heading 类型的「学位论文数据集」");

        // 验证：过滤后不存在任何 table_row（表格行全部被截断）
        long filteredTableRows = filtered.getSectionsList().stream()
                .filter(s -> "table_row".equals(s.getType()))
                .count();
        assertEquals(0, filteredTableRows,
                "过滤后不应存在任何 table_row，学位论文数据集表格应全部被截断，实际数量=" + filteredTableRows);

        // 验证：paragraph 类型的目录条目「学位论文数据集\t25」仍然保留（不被截断）
        boolean hasTocEntry = filtered.getSectionsList().stream()
                .anyMatch(s -> "paragraph".equals(s.getType())
                        && s.getText().contains("学位论文数据集"));
        assertTrue(hasTocEntry, "paragraph 类型的目录条目「学位论文数据集\\t25」应保留在过滤结果中");

        logger.info("截断效果摘要：原始 {} 个，截断 {} 个（1个heading标题行 + {} 个table_row）",
                originalCount, removedCount, tableRowCount);
    }

    @Test
    @DisplayName("真实文档：过滤后送入 IntegrityScanner，不产生「学位论文数据集」相关误报")
    public void testRealDocument_integrityNoFalsePositive() throws Exception {
        ParsedData rawData = loadRealDocumentJson();
        if (rawData == null) {
            logger.warn("跳过真实文档测试：JSON 文件不存在");
            return;
        }

        IntegrityScanner scanner = new IntegrityScanner();
        List<Issue> issues = scanner.checkIntegrity(rawData);

        logger.info("完整性检查共发现 {} 个问题", issues.size());

        // 验证：没有 issue 的 originalSnippet 包含「学位论文数据集」
        boolean hasFalsePositive = issues.stream()
                .anyMatch(i -> i.getOriginalSnippet().contains("学位论文数据集")
                        || i.getMessage().contains("学位论文数据集"));
        assertFalse(hasFalsePositive, "完整性检查不应对「学位论文数据集」章节产生 Issue");

        for (Issue issue : issues) {
            logger.info("  Issue: [{}] {} @ section {}",
                    issue.getSeverity(), issue.getMessage(), issue.getSectionId());
        }
    }

    @Test
    @DisplayName("真实文档：过滤后送入 FormattingAuditor，不产生「学位论文数据集」相关误报")
    public void testRealDocument_formattingNoFalsePositive() throws Exception {
        ParsedData rawData = loadRealDocumentJson();
        if (rawData == null) {
            logger.warn("跳过真实文档测试：JSON 文件不存在");
            return;
        }

        FormattingAuditor auditor = new FormattingAuditor();
        List<Issue> issues = auditor.checkFormatting(rawData);

        logger.info("排版检查共发现 {} 个问题", issues.size());

        boolean hasFalsePositive = issues.stream()
                .anyMatch(i -> i.getOriginalSnippet().contains("学位论文数据集")
                        || i.getMessage().contains("学位论文数据集"));
        assertFalse(hasFalsePositive, "排版检查不应对「学位论文数据集」章节产生 Issue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 从 classpath 或文件系统加载真实测试 JSON，转换为 ParsedData。
     * 返回 null 表示文件不存在（测试将被跳过）。
     */
    private ParsedData loadRealDocumentJson() throws Exception {
        java.net.URL resourceUrl = getClass().getClassLoader()
                .getResource("data/audit_results_final.json");
        File jsonFile;
        if (resourceUrl != null) {
            jsonFile = new File(resourceUrl.getFile());
        } else {
            jsonFile = new File("src/test/resources/data/audit_results_final.json");
        }

        if (!jsonFile.exists()) {
            logger.warn("JSON 文件不存在: {}", jsonFile.getAbsolutePath());
            return null;
        }

        logger.info("加载真实文档 JSON: {}", jsonFile.getAbsolutePath());
        JsonNode root = objectMapper.readTree(jsonFile);
        return convertJsonToParsedData(root);
    }

    /**
     * 将 JSON 节点转换为 ParsedData（与 RealDocumentAuditTest 保持一致）。
     */
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
                    props.fields().forEachRemaining(e ->
                            sb.putProps(e.getKey(), e.getValue().asText()));
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

    /**
     * 构造一个包含指定文本列表的 ParsedData，所有 section 均为 type=paragraph。
     * 用于测试白名单等不依赖 type 的场景。
     */
    private ParsedData buildParsedData(String... texts) {
        ParsedData.Builder builder = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("测试文档")
                        .setPageCount(texts.length)
                        .build());
        for (int i = 0; i < texts.length; i++) {
            builder.addSections(Section.newBuilder()
                    .setSectionId(i + 1)
                    .setType("paragraph")   // 全部 paragraph，不触发停止检测
                    .setLevel(0)
                    .setText(texts[i])
                    .build());
        }
        return builder.build();
    }

    /**
     * 构造一个包含指定 SectionSpec 列表的 ParsedData，支持自定义 type。
     * 用于测试 heading 类型触发截断的场景。
     */
    private ParsedData buildParsedDataMixed(SectionSpec... specs) {
        ParsedData.Builder builder = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("测试文档")
                        .setPageCount(specs.length)
                        .build());
        for (int i = 0; i < specs.length; i++) {
            builder.addSections(Section.newBuilder()
                    .setSectionId(i + 1)
                    .setType(specs[i].type)
                    .setLevel("heading".equals(specs[i].type) ? 1 : 0)
                    .setText(specs[i].text)
                    .build());
        }
        return builder.build();
    }

    /** 辅助数据类：section 文本 + 类型 */
    private static class SectionSpec {
        final String text;
        final String type;
        SectionSpec(String text, String type) {
            this.text = text;
            this.type = type;
        }
    }
}
