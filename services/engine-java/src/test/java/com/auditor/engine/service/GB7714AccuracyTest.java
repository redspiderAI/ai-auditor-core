package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GB/T 7714 参考文献准确率自测
 * 包含 20 条"故意出错"的参考文献
 * 要求 Drools 必须全部检出，否则报错
 */
public class GB7714AccuracyTest {

    private ReferenceChecker referenceChecker;

    @BeforeEach
    public void setUp() {
        referenceChecker = new ReferenceChecker();
    }

    @Test
    public void testGB7714Accuracy() {
        // 构造 20 条故意出错的参考文献
        ParsedData data = buildTestData();

        // 运行检查
        List<Issue> issues = referenceChecker.checkReferences(data);

        // 打印所有检出的问题
        System.out.println("\n========== GB/T 7714 准确率自测结果 ==========");
        System.out.println("总检出问题数: " + issues.size());
        System.out.println();

        for (Issue issue : issues) {
            System.out.println("✗ [" + issue.getCode() + "] " + issue.getMessage());
        }

        // 验证检出的问题数
        // 期望检出至少 20 个问题（每条错误文献至少 1 个问题）
        assertTrue(issues.size() >= 20, 
                "准确率自测失败：检出问题数 " + issues.size() + " < 20，说明 Drools 规则漏检");

        System.out.println("\n✅ 准确率自测通过！检出 " + issues.size() + " 个问题");
    }

    private ParsedData buildTestData() {
        ParsedData.Builder builder = ParsedData.newBuilder()
                .setDocId("accuracy-test")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("GB/T 7714 准确率自测")
                        .setPageCount(1)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setLevel(1)
                        .setType("heading")
                        .setText("参考文献")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setLevel(0)
                        .setType("paragraph")
                        .setText("根据研究[1-20]表明...")
                        .build());

        // 错误1: 期刊文献，使用了全角逗号
        builder.addReferences(Reference.newBuilder()
                .setRefId("[1]")
                .setRawText("[1] 张三，李四. 论文题名[J]. 期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误2: 期刊文献，年份超出 2026
        builder.addReferences(Reference.newBuilder()
                .setRefId("[2]")
                .setRawText("[2] 张三, 李四. 论文题名[J]. 期刊名, 2027, 10(2): 1-10.")
                .build());

        // 错误3: 期刊文献，[J] 后面没有点号
        builder.addReferences(Reference.newBuilder()
                .setRefId("[3]")
                .setRawText("[3] 张三, 李四. 论文题名[J] 期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误4: 期刊文献，缺少卷(期)
        builder.addReferences(Reference.newBuilder()
                .setRefId("[4]")
                .setRawText("[4] 张三, 李四. 论文题名[J]. 期刊名, 2023, 1-10.")
                .build());

        // 错误5: 期刊文献，缺少页码
        builder.addReferences(Reference.newBuilder()
                .setRefId("[5]")
                .setRawText("[5] 张三, 李四. 论文题名[J]. 期刊名, 2023, 10(2).")
                .build());

        // 错误6: 期刊文献，多作者但没有"等"
        builder.addReferences(Reference.newBuilder()
                .setRefId("[6]")
                .setRawText("[6] 张三, 李四, 王五, 赵六. 论文题名[J]. 期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误7: 专著文献，使用了全角句号
        builder.addReferences(Reference.newBuilder()
                .setRefId("[7]")
                .setRawText("[7] 张三。 书名[M]。 北京: 出版社, 2023.")
                .build());

        // 错误8: 专著文献，年份超出 2026
        builder.addReferences(Reference.newBuilder()
                .setRefId("[8]")
                .setRawText("[8] 张三. 书名[M]. 北京: 出版社, 2030.")
                .build());

        // 错误9: 专著文献，[M] 后面没有点号
        builder.addReferences(Reference.newBuilder()
                .setRefId("[9]")
                .setRawText("[9] 张三. 书名[M] 北京: 出版社, 2023.")
                .build());

        // 错误10: 专著文献，缺少出版地:出版者
        builder.addReferences(Reference.newBuilder()
                .setRefId("[10]")
                .setRawText("[10] 张三. 书名[M]. 2023.")
                .build());

        // 错误11: 期刊文献，使用了全角分号
        builder.addReferences(Reference.newBuilder()
                .setRefId("[11]")
                .setRawText("[11] 张三, 李四. 论文题名[J]；期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误12: 专著文献，多作者但没有"等"
        builder.addReferences(Reference.newBuilder()
                .setRefId("[12]")
                .setRawText("[12] 张三, 李四, 王五, 赵六. 书名[M]. 北京: 出版社, 2023.")
                .build());

        // 错误13: 期刊文献，年份为两位数
        builder.addReferences(Reference.newBuilder()
                .setRefId("[13]")
                .setRawText("[13] 张三, 李四. 论文题名[J]. 期刊名, 23, 10(2): 1-10.")
                .build());

        // 错误14: 期刊文献，年份过早（< 1900）
        builder.addReferences(Reference.newBuilder()
                .setRefId("[14]")
                .setRawText("[14] 张三, 李四. 论文题名[J]. 期刊名, 1800, 10(2): 1-10.")
                .build());

        // 错误15: 专著文献，年份为两位数
        builder.addReferences(Reference.newBuilder()
                .setRefId("[15]")
                .setRawText("[15] 张三. 书名[M]. 北京: 出版社, 99.")
                .build());

        // 错误16: 期刊文献，混用中英文标点
        builder.addReferences(Reference.newBuilder()
                .setRefId("[16]")
                .setRawText("[16] 张三, 李四。论文题名[J]. 期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误17: 专著文献，混用中英文标点
        builder.addReferences(Reference.newBuilder()
                .setRefId("[17]")
                .setRawText("[17] 张三。 书名[M]. 北京: 出版社, 2023.")
                .build());

        // 错误18: 期刊文献，缺少作者
        builder.addReferences(Reference.newBuilder()
                .setRefId("[18]")
                .setRawText("[18] 论文题名[J]. 期刊名, 2023, 10(2): 1-10.")
                .build());

        // 错误19: 专著文献，缺少作者
        builder.addReferences(Reference.newBuilder()
                .setRefId("[19]")
                .setRawText("[19] 书名[M]. 北京: 出版社, 2023.")
                .build());

        // 错误20: 期刊文献，[J] 标记错误（应为大写）
        builder.addReferences(Reference.newBuilder()
                .setRefId("[20]")
                .setRawText("[20] 张三, 李四. 论文题名[j]. 期刊名, 2023, 10(2): 1-10.")
                .build());

        return builder.build();
    }
}
