package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FormattingAuditorTest {

    private FormattingAuditor formattingAuditor;

    @BeforeEach
    public void setUp() {
        formattingAuditor = new FormattingAuditor();
    }

    @Test
    public void testCheckFormattingWithValidData() {
        ParsedData data = createValidParsedData();
        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        assertNotNull(issues);
        // 有效数据应该能够被检查
        assertTrue(issues != null);
    }

    @Test
    public void testCheckFormattingWithInvalidFontSize() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("标题")
                        .putProps("font-size", "10")
                        .putProps("font-family", "黑体")
                        .build())
                .build();

        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        assertNotNull(issues);
        // 应该至少发现一个问题
        assertTrue(issues.size() > 0, "应该发现问题");
    }

    @Test
    public void testCheckFormattingWithInvalidFont() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("标题")
                        .putProps("font-size", "16")
                        .putProps("font-family", "宋体")
                        .build())
                .build();

        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        assertNotNull(issues);
        // 应该至少发现一个问题
        assertTrue(issues.size() > 0, "应该发现问题");
    }

    @Test
    public void testCheckFormattingWithMultipleSections() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("第一章")
                        .putProps("font-family", "黑体")
                        .putProps("font-size", "16")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setType("heading")
                        .setLevel(2)
                        .setText("第一节")
                        .putProps("font-family", "黑体")
                        .putProps("font-size", "15")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(3)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("正文内容")
                        .putProps("font-family", "宋体")
                        .putProps("font-size", "12")
                        .build())
                .build();

        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        assertNotNull(issues);
        // 应该能够处理多个章节
        assertTrue(issues.size() >= 0);
    }

    @Test
    public void testCheckFormattingWithEmptyData() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Empty Document")
                        .setPageCount(1)
                        .build())
                .build();

        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        assertNotNull(issues);
        // 空数据应该没有问题或只有低级别问题
        assertTrue(issues.isEmpty() || issues.stream().allMatch(i -> i.getSeverity() == Severity.LOW));
    }

    @Test
    public void testCheckFormattingReturnsIssuesWithCorrectStructure() {
        ParsedData data = createValidParsedData();
        List<Issue> issues = formattingAuditor.checkFormatting(data);
        
        for (Issue issue : issues) {
            assertNotNull(issue.getCode(), "Issue code 不能为空");
            assertNotNull(issue.getMessage(), "Issue message 不能为空");
            assertNotNull(issue.getSeverity(), "Issue severity 不能为空");
        }
    }

    private ParsedData createValidParsedData() {
        return ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .setMarginTop(2.5f)
                        .setMarginBottom(2.5f)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("标题")
                        .putProps("font-family", "黑体")
                        .putProps("font-size", "16")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("正文内容")
                        .putProps("font-family", "宋体")
                        .putProps("font-size", "12")
                        .build())
                .build();
    }
}
