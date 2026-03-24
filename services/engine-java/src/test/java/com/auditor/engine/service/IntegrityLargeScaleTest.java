package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 完整性模块大规模逻辑压力测试
 * 模拟 200 组文档样本，验证 Drools 规则引擎的鲁棒性
 */
public class IntegrityLargeScaleTest {

    @Test
    public void testIntegrityWith200Samples() throws IOException {
        IntegrityScanner scanner = new IntegrityScanner();
        List<Issue> allDetectedIssues = new ArrayList<>();
        
        System.out.println(">>> 开始执行完整性模块 200 组样本测试...");

        for (int i = 1; i <= 200; i++) {
            ParsedData.Builder docBuilder = ParsedData.newBuilder()
                    .setDocId("BATCH-TEST-" + i)
                    .setMetadata(DocumentMetadata.newBuilder()
                            .setTitle("大规模测试样本第 " + i + " 号")
                            .setPageCount(i % 50 + 1) // 模拟不同页数
                            .build());

            // --- 循环注入 13 种不同的逻辑错误 ---
            
            if (i % 4 == 1) { 
                // 模式 1：致命缺失 (REQ) - 只有第一章，缺少摘要、引言、结论、文献
                docBuilder.addSections(createSection(1, "第一章 绪论", 1));
            } 
            else if (i % 4 == 2) { 
                // 模式 2：标题层级冲突 (HIER) - 1级直接跳3级
                docBuilder.addSections(createSection(1, "摘要", 1));
                docBuilder.addSections(createSection(2, "1.1.1 某种背景", 3)); 
            }
            else if (i % 4 == 3) {
                // 模式 3：重复性错误 (DUPLICATE) - ID 重复
                docBuilder.addSections(createSection(1, "摘要", 1));
                docBuilder.addSections(createSection(1, "重复的摘要", 1));
            }
            else {
                // 模式 4：不完整元数据或边界情况
                docBuilder.addSections(createSection(1, "正文内容", 0)); // 无级别的正文
            }

            // 执行 Drools 审计
            List<Issue> currentIssues = scanner.scanIntegrity(docBuilder.build());
            allDetectedIssues.addAll(currentIssues);
        }

        // 将 200 组测试的明细汇总写入 TXT 文件
        writeReportToTxt(allDetectedIssues);

        System.out.println(">>> 测试完成！");
        System.out.println(">>> 总计审计样本数: 200");
        System.out.println(">>> 规则引擎发现问题总数: " + allDetectedIssues.size());
        System.out.println(">>> 报告生成路径: services/engine-java/target/integrity_audit_detail.txt");
    }

    private Section createSection(int id, String text, int level) {
        return Section.newBuilder()
                .setSectionId(id)
                .setText(text)
                .setType(level > 0 ? "heading" : "paragraph")
                .setLevel(level)
                .build();
    }

    private void writeReportToTxt(List<Issue> issues) throws IOException {
        File targetDir = new File("target");
        if (!targetDir.exists()) targetDir.mkdirs();

        String filePath = "target/integrity_audit_detail.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("完整性模块大规模审计明细报告");
            writer.println("测试时间: " + LocalDateTime.now());
            writer.println("样本总数: 200 组");
            writer.println("==================================================");
            
            for (int i = 0; i < issues.size(); i++) {
                Issue issue = issues.get(i);
                writer.printf("[%03d] Code: %-15s | Severity: %-8s | Msg: %s%n", 
                    i + 1,
                    issue.getCode(),
                    issue.getSeverity(),
                    issue.getMessage());
            }
            writer.println("==================================================");
            writer.println("报告结束 - 共计 " + issues.size() + " 条记录");
        }
    }
}