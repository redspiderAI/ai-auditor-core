package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrityScannerTest {

    private IntegrityScanner integrityScanner;

    @BeforeEach
    public void setUp() {
        integrityScanner = new IntegrityScanner();
    }

    @Test
    public void testScanIntegrityWithValidData() {
        ParsedData data = createValidParsedData();
        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
        assertNotNull(issues);
        assertTrue(issues.stream().allMatch(i -> i.getSeverity() != Severity.CRITICAL));
    }

    @Test
    public void testScanIntegrityWithMissingChapters() {
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
                        .build())
                .build();

        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
        assertNotNull(issues);
        assertTrue(issues.size() > 0, "应该发现缺失的必备章节");
        assertTrue(issues.stream().anyMatch(i -> i.getCode().contains("REQ")), 
                   "应该有 REQ 相关的问题");
    }

    @Test
    public void testScanIntegrityWithCompleteStructure() {
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
                        .setText("摘要")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setType("heading")
                        .setLevel(1)
                        .setText("引言")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(3)
                        .setType("heading")
                        .setLevel(1)
                        .setText("正文")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(4)
                        .setType("heading")
                        .setLevel(1)
                        .setText("结论")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(5)
                        .setType("heading")
                        .setLevel(1)
                        .setText("参考文献")
                        .build())
                .build();

        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
        assertNotNull(issues);
        assertFalse(issues.stream().anyMatch(i -> i.getCode().contains("REQ")), 
                    "不应该有缺失必备章节的问题");
    }

    @Test
    public void testScanIntegrityWithInvalidHeadingHierarchy() {
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
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setType("heading")
                        .setLevel(3)
                        .setText("第一节")
                        .build())
                .build();

        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
        assertNotNull(issues);
        assertTrue(issues.stream().anyMatch(i -> i.getCode().contains("HIER")), 
                   "应该发现标题层级问题");
    }

    @Test
    public void testScanIntegrityWithDuplicateHeadings() {
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
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("第二章")
                        .build())
                .build();

        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
        assertNotNull(issues);
        assertTrue(issues.stream().anyMatch(i -> i.getCode().contains("DUPLICATE")), 
                   "应该发现重复章节问题");
    }

    @Test
    public void testScanIntegrityReturnsIssuesWithCorrectStructure() {
        ParsedData data = createValidParsedData();
        List<Issue> issues = integrityScanner.scanIntegrity(data);
        
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
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("heading")
                        .setLevel(1)
                        .setText("摘要")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(2)
                        .setType("heading")
                        .setLevel(1)
                        .setText("引言")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(3)
                        .setType("heading")
                        .setLevel(1)
                        .setText("正文")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(4)
                        .setType("heading")
                        .setLevel(1)
                        .setText("结论")
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(5)
                        .setType("heading")
                        .setLevel(1)
                        .setText("参考文献")
                        .build())
                .build();
    }
}
