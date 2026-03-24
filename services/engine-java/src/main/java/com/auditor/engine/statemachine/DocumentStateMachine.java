package com.auditor.engine.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文档状态机
 * 
 * 功能：管理文档在审计流程中的各种状态和转移
 */
@Component
public class DocumentStateMachine {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentStateMachine.class);
    
    public enum DocumentState {
        INITIAL("初始状态"),
        PARSING("解析中"),
        PARSED("解析完成"),
        AUDITING("审计中"),
        AUDIT_COMPLETE("审计完成"),
        FAILED("失败"),
        ARCHIVED("归档");
        
        private final String description;
        
        DocumentState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private String documentId = "default-doc";
    private DocumentState currentState = DocumentState.INITIAL;
    
    /**
     * 默认构造函数，供 Spring 使用
     */
    public DocumentStateMachine() {
        logger.info("初始化全局文档状态机");
    }
    
    public DocumentStateMachine(String documentId) {
        this.documentId = documentId;
        logger.info("创建文档状态机: {}", documentId);
    }
    
    public DocumentState getCurrentState() {
        return currentState;
    }
    
    public void transition(DocumentState nextState) {
        this.currentState = nextState;
        logger.info("文档 {} 状态转移至: {}", documentId, nextState.getDescription());
    }
}