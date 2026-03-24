package com.auditor.engine.test;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Drools 测试辅助类，用于在测试环境中初始化 Drools 规则引擎
 */
public class DroolsTestHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(DroolsTestHelper.class);
    private static KieContainer kieContainer;
    
    /**
     * 初始化 Drools 规则引擎
     */
    public static synchronized KieContainer initializeDrools() {
        if (kieContainer != null) {
            return kieContainer;
        }
        
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieRepository kieRepository = kieServices.getRepository();
            KieFileSystem kfs = kieServices.newKieFileSystem();
            
            // 加载所有规则文件
            loadRuleFiles(kfs);
            
            // 构建 KieModule
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();
            
            if (kieBuilder.getResults().hasMessages()) {
                logger.error("Drools 规则构建失败:");
                kieBuilder.getResults().getMessages().forEach(msg -> 
                    logger.error("  - {}", msg.toString())
                );
                throw new RuntimeException("Drools 规则构建失败");
            }
            
            kieContainer = kieServices.newKieContainer(kieRepository.getDefaultReleaseId());
            logger.info("Drools 规则引擎初始化成功");
            return kieContainer;
            
        } catch (Exception e) {
            logger.error("Drools 初始化失败", e);
            throw new RuntimeException("无法初始化 Drools", e);
        }
    }
    
    /**
     * 加载规则文件
     */
    private static void loadRuleFiles(KieFileSystem kfs) throws IOException {
        String[] rulePaths = {
            "src/main/resources/rules/formatting/formatting.drl",
            "src/main/resources/rules/reference/reference.drl",
            "src/main/resources/rules/integrity/integrity.drl"
        };
        
        for (String rulePath : rulePaths) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(rulePath)));
                String resourcePath = "src/main/resources/" + rulePath.substring(rulePath.lastIndexOf("/") + 1);
                kfs.write(resourcePath, content);
                logger.debug("已加载规则文件: {}", rulePath);
            } catch (IOException e) {
                logger.warn("无法加载规则文件 {}: {}", rulePath, e.getMessage());
            }
        }
    }
    
    /**
     * 创建 KieSession
     */
    public static KieSession createKieSession(String sessionName) {
        if (kieContainer == null) {
            initializeDrools();
        }
        return kieContainer.newKieSession(sessionName);
    }
    
    /**
     * 关闭 KieContainer
     */
    public static void shutdown() {
        if (kieContainer != null) {
            kieContainer.dispose();
            kieContainer = null;
        }
    }
}
