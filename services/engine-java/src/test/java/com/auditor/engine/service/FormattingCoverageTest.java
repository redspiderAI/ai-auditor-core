package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FormattingCoverageTest {

    private static final Logger logger = LoggerFactory.getLogger(FormattingCoverageTest.class);
    private FormattingAuditor formattingAuditor;

    @BeforeEach
    public void setUp() {
        formattingAuditor = new FormattingAuditor();
    }

    @Test
    public void testFormattingAccuracy200() throws IOException {
        // 1. 生成测试数据
        ParsedData data = generate200FormattingTestData();
        
        // 2. 执行审计检查
        List<Issue> issues = formattingAuditor.checkFormatting(data);

        // 3. 输出每一条具体记录
        System.out.println("\n>>>>>> 审计模块详细明细输出开始 <<<<<<");
        for (Issue issue : issues) {
            System.out.printf("ID: %-4d | Code: %-25s | Suggestion: %s%n", 
                issue.getSectionId(), 
                issue.getCode(), 
                issue.getMessage());
        }
        System.out.println(">>>>>> 审计模块详细明细输出结束 <<<<<<\n");

        // 4. 统计各规则检出情况
        Map<String, Integer> detectionStats = new TreeMap<>();
        for (Issue issue : issues) {
            detectionStats.put(issue.getCode(), detectionStats.getOrDefault(issue.getCode(), 0) + 1);
        }

        logger.info("=== 排版规则准确率测试统计 ===");
        logger.info("总测试样本数: 200");
        logger.info("总检出问题数: {}", issues.size());
        
        detectionStats.forEach((code, count) -> {
            logger.info("规则 [{}] 检出次数: {}", code, count);
        });

        // 5. 保存详细报告到文件
        saveDetailReport(issues, detectionStats);

        // 6. 断言：200条数据全部有格式问题，检出数应 >= 190
        assertTrue(issues.size() >= 190, "排版规则检出率过低，当前检出数: " + issues.size());
    }

    /**
     * 将 200 条明细保存到 target 目录下的 txt 文件中
     */
    private void saveDetailReport(List<Issue> issues, Map<String, Integer> stats) throws IOException {
        File reportFile = new File("target/formatting_audit_detail.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("审计模块详细明细报告 - " + LocalDateTime.now());
            writer.println("==================================================");
            
            for (Issue issue : issues) {
                writer.printf("ID: %-5d | Code: %-25s | Suggestion: %s%n", 
                    issue.getSectionId(), issue.getCode(), issue.getMessage());
            }

            writer.println("\n================ 统计摘要 ================");
            stats.forEach((code, count) -> writer.printf("%-25s : %d 条%n", code, count));
            writer.println("总计检出: " + issues.size() + " 条记录");
        }
        logger.info(">>> 详细明细报告已生成至: {}", reportFile.getAbsolutePath());
    }

    private ParsedData generate200FormattingTestData() {
        ParsedData.Builder builder = ParsedData.newBuilder()
                .setDocId("TEST_FMT_200");

        int sectionIdCounter = 1;

        // ── formatting.drl 规则对应关系 ──────────────────────────────────────────
        // 规则1  FMT_LINE_HEIGHT_001  : paragraph/heading，line-height 不在 1.5±0.05
        // 规则5  FMT_HEADING_FONT_001 : heading level=1，font-family 不含"黑体"
        // 规则6  FMT_HEADING_SIZE_001 : heading level=1，font-size 不在 18±1
        // 规则7  FMT_HEADING_FONT_002 : heading level=2，font-family 不含"黑体"
        // 规则8  FMT_HEADING_SIZE_002 : heading level=2，font-size 不在 16±1
        // 规则11 FMT_BODY_FONT_001    : paragraph，font-family 不含"宋体"/"仿宋"
        // 规则12 FMT_BODY_SIZE_001    : paragraph，font-size 不在 12±1
        // 规则4  FMT_FORMULA_ALIGNMENT: formula，alignment != "right"
        // 规则3  FMT_TABLE_PAGE_BREAK : table，page-break=true 且 continue-table-flag=false
        // 规则2  FMT_HEADING_LEVEL_JUMP: 连续两个 heading 层级跳跃（如 1→3）
        // ─────────────────────────────────────────────────────────────────────────

        // 1. 行距检查 (FMT_LINE_HEIGHT_001) - 20条
        //    paragraph + line-height=2.0（不在 1.5±0.05）→ 触发行距规则
        //    font-family="Arial"（不含"宋体"/"仿宋"）→ 同时触发 FMT_BODY_FONT_001
        for (int i = 0; i < 20; i++) {
            double invalidLineHeight = 2.0 + (i * 0.1);
            builder.addSections(createSection(sectionIdCounter++, "paragraph", 0, "行距错误样本",
                Map.of("line-height", String.valueOf(invalidLineHeight),
                       "font-family", "Arial",
                       "font-size",   "12")));
        }

        // 2. 一级标题字体 (FMT_HEADING_FONT_001) - 20条
        //    heading level=1，font-family="Arial"（不含"黑体"）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "heading", 1, "一级标题字体错误",
                Map.of("font-family", "Arial",
                       "font-size",   "18")));
        }

        // 3. 一级标题字号 (FMT_HEADING_SIZE_001) - 20条
        //    heading level=1，font-family="黑体"（通过字体检查），font-size=14（不在 18±1）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "heading", 1, "一级标题字号错误",
                Map.of("font-family", "黑体",
                       "font-size",   "14")));
        }

        // 4. 二级标题字体 (FMT_HEADING_FONT_002) - 20条
        //    heading level=2，font-family="Arial"（不含"黑体"）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "heading", 2, "二级标题字体错误",
                Map.of("font-family", "Arial",
                       "font-size",   "16")));
        }

        // 5. 二级标题字号 (FMT_HEADING_SIZE_002) - 20条
        //    heading level=2，font-family="黑体"（通过字体检查），font-size=12（不在 16±1）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "heading", 2, "二级标题字号错误",
                Map.of("font-family", "黑体",
                       "font-size",   "12")));
        }

        // 6. 正文字体检查 (FMT_BODY_FONT_001) - 20条
        //    paragraph，font-family="Microsoft YaHei"（不含"宋体"/"仿宋"）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "paragraph", 0, "正文字体错误",
                Map.of("font-family", "Microsoft YaHei",
                       "font-size",   "12")));
        }

        // 7. 正文字号检查 (FMT_BODY_SIZE_001) - 20条
        //    paragraph，font-family="宋体"（通过字体检查），font-size=10（不在 12±1）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "paragraph", 0, "正文字号错误",
                Map.of("font-family", "宋体",
                       "font-size",   "10")));
        }

        // 8. 公式对齐检查 (FMT_FORMULA_ALIGNMENT) - 20条
        //    formula，alignment="left"（不等于 "right"）→ 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "formula", 0, "E=mc^2",
                Map.of("alignment", "left")));
        }

        // 9. 表格跨页续表标志 (FMT_TABLE_PAGE_BREAK) - 20条
        //    table，page-break=true 且 continue-table-flag=false → 触发
        for (int i = 0; i < 20; i++) {
            builder.addSections(createSection(sectionIdCounter++, "table", 0, "数据表",
                Map.of("page-break", "true", "continue-table-flag", "false")));
        }

        // 10. 标题层级跳跃 (FMT_HEADING_LEVEL_JUMP) - 20条
        //     heading(level=1) 紧接 heading(level=3)，跳跃超过1级 → 触发
        for (int i = 0; i < 10; i++) {
            builder.addSections(createSection(sectionIdCounter++, "heading", 1, "章标题", Map.of()));
            builder.addSections(createSection(sectionIdCounter++, "heading", 3, "跳级标题", Map.of()));
        }

        return builder.build();
    }

    private Section createSection(int id, String type, int level, String text, Map<String, String> props) {
        return Section.newBuilder()
                .setSectionId(id)
                .setType(type)
                .setLevel(level)
                .setText(text)
                .putAllProps(props)
                .build();
    }
}
