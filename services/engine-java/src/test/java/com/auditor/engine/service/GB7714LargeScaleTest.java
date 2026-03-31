package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GB/T 7714 大规模压力测试 - 模拟 20 种真实错误类型
 * 自动生成 200 条随机内容的参考文献及 200 个排版段落
 */
public class GB7714LargeScaleTest {
    
    private static final Logger logger = LoggerFactory.getLogger(GB7714LargeScaleTest.class);
    private final ReferenceChecker referenceChecker = new ReferenceChecker();
    private final FormattingAuditor formattingAuditor = new FormattingAuditor();
    
    // 随机素材池
    private final String[] AUTHORS = {"张三", "李四", "王五", "赵六", "钱七", "Sun W.", "James A.", "Liu Y.", "Chen H."};
    private final String[] TITLES = {"深度学习研究", "区块链审计系统", "大语言模型综述", "分布式系统设计", "规则引擎实战"};
    private final String[] JOURNALS = {"计算机学报", "软件学报", "IEEE Transactions", "Nature", "Science"};
    private final String[] PUBLISHERS = {"科学出版社", "清华大学出版社", "Springer", "Elsevier"};

    @Test
    public void testGB7714With200References() throws Exception {
        // 1. 生成 200 条具有随机性的测试数据
        ParsedData data = generateLargeScaleTestData();
        
        // 2. 执行审计
        List<Issue> issues = referenceChecker.checkReferences(data);
        
        // 3. 统计结果并打印
        printSummary("GB/T 7714 参考文献大规模审计", 200, issues);
        
        // 4. 打印前 40 条明细采样
        System.out.println("\n>>> [审计证据采样] 参考文献明细 (前40条):");
        System.out.printf("%-6s | %-30s | %-25s | %s\n", "序号", "错误代码", "文本片段", "建议");
        System.out.println("---------------------------------------------------------------------------------------");
        
        issues.stream().limit(40).forEach(issue -> {
            String snippet = issue.getOriginalSnippet().length() > 20 
                ? issue.getOriginalSnippet().substring(0, 20).replace("\n", "") + "..." 
                : issue.getOriginalSnippet().replace("\n", "");
            
            System.out.printf("Ref    | %-30s | %-25s | %s\n", 
                issue.getCode(), 
                snippet, 
                issue.getSuggestion());
        });

        // 5. 持久化到文件
        saveIssuesToFile(issues, "target/reference_audit_detail.txt");
        assertTrue(issues.size() >= 200, "200条测试数据应至少检出200个问题");
    }

    @Test
    public void testFormattingWith200Sections() throws Exception {
        ParsedData data = generateFormattingTestData();
        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        printSummary("排版规则大规模审计", 200, issues);
        saveIssuesToFile(issues, "target/formatting_audit_detail.txt");
        assertTrue(issues.size() > 0);
    }

    private ParsedData generateLargeScaleTestData() {
        ParsedData.Builder builder = ParsedData.newBuilder().setDocId("REF-VOL-2026");
        
        // 生成 200 条，循环 10 次 20 种错误
        for (int i = 1; i <= 200; i++) {
            int type = (i - 1) % 20; 
            String author = AUTHORS[i % AUTHORS.length];
            String title = TITLES[i % TITLES.length];
            String journal = JOURNALS[i % JOURNALS.length];
            String pub = PUBLISHERS[i % PUBLISHERS.length];
            int year = 2010 + (i % 15); 

            String rawText;
            switch (type) {
                case 0: rawText = String.format("[%d] %s，%s. %s[J]. %s, %d.", i, author, "李四", title, journal, year); break;
                case 1: rawText = String.format("[%d] %s, %s. %s[J]. %s, %d.", i, author, "李四", title, journal, 2027 + i % 10); break;
                case 2: rawText = String.format("[%d] %s, %s. %s[J] %s, %d.", i, author, "李四", title, journal, year); break;
                case 3: rawText = String.format("[%d] %s, %s. %s[J]. %s, %d, 1-10.", i, author, "李四", title, journal, year); break;
                case 4: rawText = String.format("[%d] %s, %s. %s[J]. %s, %d, 10(2).", i, author, "李投", title, journal, year); break;
                case 5: rawText = String.format("[%d] %s, 李四, 王五, 赵六. %s[J]. %s, %d.", i, author, title, journal, year); break;
                case 6: rawText = String.format("[%d] %s。 %s[M]。 北京: %s, %d.", i, author, title, pub, year); break;
                case 7: rawText = String.format("[%d] %s. %s[M]. 北京: %s, %d.", i, author, title, pub, 2030 + (i % 5)); break;
                case 8: rawText = String.format("[%d] %s. %s[M] 北京: %s, %d.", i, author, title, pub, year); break;
                case 9: rawText = String.format("[%d] %s. %s[M]. %d.", i, author, title, year); break;
                case 10: rawText = String.format("[%d] %s, %s. %s[J]；%s, %d.", i, author, "李四", title, journal, year); break;
                case 11: rawText = String.format("[%d] %s, 李四, 王五, 赵六. %s[M]. 北京: %s, %d.", i, author, title, pub, year); break;
                case 12: rawText = String.format("[%d] %s, %s. %s[J]. %s, %d.", i, author, "李四", title, journal, i % 99); break;
                case 13: rawText = String.format("[%d] %s, %s. %s[J]. %s, 1850.", i, author, "李四", title, journal); break;
                case 14: rawText = String.format("[%d] %s. %s[M]. 北京: %s, 95.", i, author, title, pub); break;
                case 15: rawText = String.format("[%d] %s, %s。%s[J]. %s, %d.", i, author, "李四", title, journal, year); break;
                case 16: rawText = String.format("[%d] %s。 %s[M]. 北京: %s, %d.", i, author, title, pub, year); break;
                case 17: rawText = String.format("[%d] %s[J]. %s, %d.", i, title, journal, year); break;
                case 18: rawText = String.format("[%d] %s[M]. 北京: %s, %d.", i, title, pub, year); break;
                case 19: 
                default: rawText = String.format("[%d] %s, %s. %s[j]. %s, %d.", i, author, "李四", title, journal, year); break;
            }
            
            builder.addReferences(Reference.newBuilder()
                    .setRefId("[" + i + "]")
                    .setRawText(rawText)
                    .build());
        }
        return builder.build();
    }

    private ParsedData generateFormattingTestData() {
        ParsedData.Builder builder = ParsedData.newBuilder().setDocId("F-200");
        // 生成 200 个 section，每种类型循环覆盖 5 种格式错误
        // 规则引擎使用 getPropsMap().get(key) 读取 props，key 为连字符格式
        for (int i = 1; i <= 200; i++) {
            int errorType = (i - 1) % 5;
            Section.Builder sb = Section.newBuilder()
                    .setSectionId(i)
                    .setText("测试排版段落 " + i);
            switch (errorType) {
                case 0:
                    // 行距不符合标准（应为 1.5，给 2.0 触发 FMT_LINE_HEIGHT_001）
                    sb.setType("paragraph")
                      .putProps("line-height", "2.0")
                      .putProps("font-family", "SimSun")
                      .putProps("font-size", "12");
                    break;
                case 1:
                    // 正文字体错误（应为 SimSun，给 Arial 触发字体规则）
                    sb.setType("paragraph")
                      .putProps("line-height", "1.5")
                      .putProps("font-family", "Arial")
                      .putProps("font-size", "12");
                    break;
                case 2:
                    // 正文字号错误（应为 12，给 14 触发字号规则）
                    sb.setType("paragraph")
                      .putProps("line-height", "1.5")
                      .putProps("font-family", "SimSun")
                      .putProps("font-size", "14");
                    break;
                case 3:
                    // 对齐方式错误（应为 LEFT，给 CENTER 触发对齐规则）
                    sb.setType("paragraph")
                      .putProps("line-height", "1.5")
                      .putProps("font-family", "SimSun")
                      .putProps("font-size", "12")
                      .putProps("alignment", "CENTER");
                    break;
                case 4:
                    // 标题字体错误（应为 SimHei，给 Arial）
                    sb.setType("heading")
                      .setLevel(1)
                      .putProps("line-height", "1.5")
                      .putProps("font-family", "Arial")
                      .putProps("font-size", "15");
                    break;
                default:
                    sb.setType("paragraph")
                      .putProps("line-height", "2.0")
                      .putProps("font-family", "Arial")
                      .putProps("font-size", "14");
            }
            builder.addSections(sb.build());
        }
        return builder.build();
    }

    private void saveIssuesToFile(List<Issue> issues, String filePath) throws Exception {
        try (PrintWriter writer = new PrintWriter(filePath, StandardCharsets.UTF_8)) {
            writer.println("审计模块详细明细报告 - " + new Date());
            writer.println("==================================================");
            for (Issue issue : issues) {
                writer.printf("ID: %d | Code: %s | Suggestion: %s | Text: %s\n", 
                    issue.getSectionId(), issue.getCode(), issue.getSuggestion(), issue.getOriginalSnippet().replace("\n", " "));
            }
        }
    }

    private void printSummary(String title, int total, List<Issue> issues) {
        Map<String, Integer> stats = new TreeMap<>();
        issues.forEach(i -> stats.put(i.getCode(), stats.getOrDefault(i.getCode(), 0) + 1));
        System.out.println("\n========== " + title + " ==========");
        System.out.println("样本总数: " + total + " | 检出总数: " + issues.size());
        stats.forEach((code, count) -> System.out.println("  - " + code + ": " + count + " 条"));
    }
}