package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 准确率测试：200 条参考文献，每条整好 1 个错误
 * 用于计算真实的检出准确率
 */
public class AccuracyTest200References {
    
    private static final Logger logger = LoggerFactory.getLogger(AccuracyTest200References.class);
    
    @Test
    public void testAccuracy200References() {
        // 生成 200 条参考文献，每条整好 1 个错误
        List<Reference> references = generate200References();
        
        // 构建 ParsedData
        ParsedData data = ParsedData.newBuilder()
            .addAllReferences(references)
            .build();
        
        // 运行检查
        ReferenceChecker checker = new ReferenceChecker();
        List<Issue> issues = checker.checkReferences(data);
        
        // 统计结果
        int totalReferences = references.size();
        int detectedIssues = issues.size();
        
        // 计算准确率
        // 只计算格式错误(ERR_开头)，排除未被引用的警告(WARN_开头)
        int formatErrors = 0;
        for (Issue issue : issues) {
            if (issue.getCode().startsWith("ERR_")) {
                formatErrors++;
            }
        }
        
        // 准确率 = 有格式错误的文献数 / 总文献数
        // 因为每条文献只有 1 个错误，所以准确率 = 格式错误数 / 总文献数
        double accuracy = (formatErrors * 100.0) / totalReferences;
        
        logger.info("========== 准确率测试结果 ==========");
        logger.info("总参考文献数: {}", totalReferences);
        logger.info("格式错误数: {}", formatErrors);
        logger.info("其他问题数: {}", detectedIssues - formatErrors);
        logger.info(String.format("准确率: %.2f%%", accuracy));
        logger.info("=====================================");
        
        // 打印所有检出的问题
        logger.info("\n检出的问题详情:");
        for (Issue issue : issues) {
            logger.info("✓ [{}] {}", issue.getCode(), issue.getMessage());
        }
        
        // 验证准确率 > 98%
        assertTrue(accuracy >= 98.0, 
            String.format("准确率 %.2f%% < 98%%，检出问题数 %d < %d", 
                accuracy, detectedIssues, (int)(totalReferences * 0.98)));
    }
    
    /**
     * 生成 200 条参考文献，每条整好 1 个错误
     * 
     * 错误分布：
     * - 期刊 [J]: 50 条（每种错误 10 条）
     *   - 全角逗号: 10 条
     *   - 年份超出范围: 10 条
     *   - [J] 后无点号: 10 条
     *   - 缺少卷期: 10 条
     *   - 缺少页码: 10 条
     * - 专著 [M]: 50 条（每种错误 10 条）
     *   - 全角句号: 10 条
     *   - 年份超出范围: 10 条
     *   - [M] 后无点号: 10 条
     *   - 缺少出版地: 10 条
     *   - 缺少出版者: 10 条
     * - 学位论文 [D]: 50 条（每种错误 10 条）
     *   - 全角句号: 10 条
     *   - 年份超出范围: 10 条
     *   - [D] 后无点号: 10 条
     *   - 缺少学位授予单位: 10 条
     *   - 缺少年份: 10 条
     * - 会议录 [C]: 50 条（每种错误 10 条）
     *   - 全角逗号: 10 条
     *   - 年份超出范围: 10 条
     *   - [C] 后无点号: 10 条
     *   - 缺少会议地点: 10 条
     *   - 缺少会议名称: 10 条
     */
    private static List<Reference> generate200References() {
        List<Reference> references = new ArrayList<>();
        int refId = 1;
        
        // ============ 期刊 [J] - 50 条 ============
        
        // 错误 1: 全角逗号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + "。论文题名[J]. 期刊名，2020, 10(1): 1-10.")
                .build());
            refId++;
        }
        
        // 错误 2: 年份超出范围 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[J]. 期刊名, " + (2027 + i) + ", 10(1): 1-10.")
                .build());
            refId++;
        }
        
        // 错误 3: [J] 后无点号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[J] 期刊名, 2020, 10(1): 1-10.")
                .build());
            refId++;
        }
        
        // 错误 4: 缺少卷期 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[J]. 期刊名, 2020: 1-10.")
                .build());
            refId++;
        }
        
        // 错误 5: 缺少页码 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[J]. 期刊名, 2020, 10(1).")
                .build());
            refId++;
        }
        
        // ============ 专著 [M] - 50 条 ============
        
        // 错误 6: 全角句号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 书名[M]。北京: 出版社, 2020。")
                .build());
            refId++;
        }
        
        // 错误 7: 年份超出范围 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 书名[M]. 北京: 出版社, " + (2050 + i) + ".")
                .build());
            refId++;
        }
        
        // 错误 8: [M] 后无点号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 书名[M] 北京: 出版社, 2020.")
                .build());
            refId++;
        }
        
        // 错误 9: 缺少出版地 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 书名[M]. 出版社, 2020.")
                .build());
            refId++;
        }
        
        // 错误 10: 缺少出版者 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 书名[M]. 北京:, 2020.")
                .build());
            refId++;
        }
        
        // ============ 学位论文 [D] - 50 条 ============
        
        // 错误 11: 全角句号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[D]。北京: 清华大学, 2020。")
                .build());
            refId++;
        }
        
        // 错误 12: 年份超出范围 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[D]. 北京: 清华大学, " + (2040 + i) + ".")
                .build());
            refId++;
        }
        
        // 错误 13: [D] 后无点号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[D] 北京: 清华大学, 2020.")
                .build());
            refId++;
        }
        
        // 错误 14: 缺少学位授予单位 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[D]. 2020.")
                .build());
            refId++;
        }
        
        // 错误 15: 缺少年份 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[D]. 北京: 清华大学.")
                .build());
            refId++;
        }
        
        // ============ 会议录 [C] - 50 条 ============
        
        // 错误 16: 全角逗号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + "。论文题名[C]. 会议名，北京，2020.")
                .build());
            refId++;
        }
        
        // 错误 17: 年份超出范围 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[C]. 会议名, 北京, " + (2099 + i) + ".")
                .build());
            refId++;
        }
        
        // 错误 18: [C] 后无点号 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[C] 会议名, 北京, 2020.")
                .build());
            refId++;
        }
        
        // 错误 19: 缺少会议地点 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[C]. 会议名, 2020.")
                .build());
            refId++;
        }
        
        // 错误 20: 缺少会议名称 - 10 条
        for (int i = 0; i < 10; i++) {
            references.add(Reference.newBuilder()
                .setRefId("[" + refId + "]")
                .setRawText("[" + refId + "] 作者" + i + ". 论文题名[C]. 北京, 2020.")
                .build());
            refId++;
        }
        
        return references;
    }
}
