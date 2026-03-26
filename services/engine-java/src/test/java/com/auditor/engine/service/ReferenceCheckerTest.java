package com.auditor.engine.service;

import com.auditor.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ReferenceCheckerTest {

    private ReferenceChecker referenceChecker;

    @BeforeEach
    public void setUp() {
        referenceChecker = new ReferenceChecker();
    }

    @Test
    public void testCheckReferencesWithValidData() {
        ParsedData data = createValidParsedDataWithReferences();
        List<Issue> issues = referenceChecker.checkReferences(data);
        
        assertNotNull(issues);
        // 有效数据应该没有 CRITICAL 级别的问题
        assertFalse(issues.stream().anyMatch(i -> i.getSeverity() == Severity.CRITICAL));
    }

    @Test
    public void testCheckReferencesWithMissingReferences() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("根据研究[1]表明...")
                        .build())
                .build();

        List<Issue> issues = referenceChecker.checkReferences(data);
        
        assertNotNull(issues);
        // 应该发现缺失的参考文献问题
        assertTrue(issues.size() > 0, "应该发现缺失的参考文献问题");
    }

    @Test
    public void testCheckReferencesWithValidReferences() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("根据研究[1]表明...")
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[1]")
                        .setRawText("Smith, J. Research Paper. Journal, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .build();

        List<Issue> issues = referenceChecker.checkReferences(data);
        
        assertNotNull(issues);
        // 有效的参考文献应该没有 CRITICAL 问题
        assertFalse(issues.stream().anyMatch(i -> i.getSeverity() == Severity.CRITICAL));
    }

    @Test
    public void testCheckReferencesWithMultipleReferences() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("根据研究[1]和[2]表明...")
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[1]")
                        .setRawText("Author1. Title1. Journal1, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[2]")
                        .setRawText("Author2. Title2. Journal2, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .build();

        List<Issue> issues = referenceChecker.checkReferences(data);
        
        assertNotNull(issues);
        // 多个有效的参考文献应该没有 CRITICAL 问题
        assertFalse(issues.stream().anyMatch(i -> i.getSeverity() == Severity.CRITICAL));
    }

    @Test
    public void testCheckReferencesWithUnusedReferences() {
        ParsedData data = ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("根据研究[1]表明...")
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[1]")
                        .setRawText("Author1. Title1. Journal1, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[2]")
                        .setRawText("Author2. Title2. Journal2, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .build();

        List<Issue> issues = referenceChecker.checkReferences(data);
        
        assertNotNull(issues);
        // 应该至少发现一个问题（未使用的参考文献）
        assertTrue(issues.size() > 0, "应该发现问题");
    }

    @Test
    public void testCheckReferencesReturnsIssuesWithCorrectStructure() {
        ParsedData data = createValidParsedDataWithReferences();
        List<Issue> issues = referenceChecker.checkReferences(data);
        
        for (Issue issue : issues) {
            assertNotNull(issue.getCode(), "Issue code 不能为空");
            assertNotNull(issue.getMessage(), "Issue message 不能为空");
            assertNotNull(issue.getSeverity(), "Issue severity 不能为空");
        }
    }

    private ParsedData createValidParsedDataWithReferences() {
        return ParsedData.newBuilder()
                .setDocId("test-doc")
                .setMetadata(DocumentMetadata.newBuilder()
                        .setTitle("Test Document")
                        .setPageCount(10)
                        .build())
                .addSections(Section.newBuilder()
                        .setSectionId(1)
                        .setType("paragraph")
                        .setLevel(0)
                        .setText("根据研究[1]表明...")
                        .build())
                .addReferences(Reference.newBuilder()
                        .setRefId("[1]")
                        .setRawText("Smith, J. Research Paper. Journal, 2023.")
                        .setIsValidFormat(true)
                        .build())
                .build();
    }
}
