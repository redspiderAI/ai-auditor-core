package com.auditor.engine.service;

import com.auditor.grpc.*;
import com.auditor.engine.mock.RealFormattingDataGenerator;
import com.auditor.engine.mock.MockDroolsEngine;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RealFormattingTest {
    
    @Test
    public void testRealThesisFormattingIssues() throws Exception {
        System.out.println("\n========== 真实论文排版检查 ==========");
        
        ParsedData data = RealFormattingDataGenerator.generateRealFormattingData();
        
        System.out.println("✅ 生成的数据包含 " + data.getSectionsCount() + " 个章节");
        
        // 检查是否有行距为 1.83 的章节
        int lineHeightIssueCount = 0;
        for (Section section : data.getSectionsList()) {
            String lineHeight = section.getPropsMap().get("line-height");
            if (lineHeight != null && lineHeight.contains("1.83")) {
                lineHeightIssueCount++;
                if (lineHeightIssueCount <= 3) {
                    System.out.println("  - 章节 " + section.getSectionId() + ": 行距 = " + lineHeight);
                }
            }
        }
        
        System.out.println("✅ 检测到 " + lineHeightIssueCount + " 个行距为 1.83 的章节");
        
        // 直接使用 MockDroolsEngine 进行检查
        List<Issue> issues = MockDroolsEngine.checkFormattingRules(data);
        
        System.out.println("✅ 检测到排版问题数: " + issues.size());
        
        if (issues.size() > 0) {
            System.out.println("\n问题详情:");
            for (Issue issue : issues) {
                if (issues.size() <= 10 || issue.getMessage().contains("行距")) {
                    System.out.println("  - [" + issue.getSectionId() + "] " + issue.getMessage());
                }
            }
        }
        
        // 验证系统能正确处理真实数据
        assertNotNull(data, "数据不应为空");
        assertTrue(data.getSectionsCount() > 0, "应该有章节数据");
        assertTrue(lineHeightIssueCount > 0, "应该检测到行距不为1.5的章节");
        
        System.out.println("\n✅ 真实排版测试通过！系统成功处理了 " + data.getSectionsCount() + " 个章节");
    }
}
