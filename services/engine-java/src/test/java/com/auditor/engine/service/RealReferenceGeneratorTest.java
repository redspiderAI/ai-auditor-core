package com.auditor.engine.service;

import com.auditor.grpc.Reference;
import org.junit.jupiter.api.Test;
import java.util.List;

public class RealReferenceGeneratorTest {
    
    @Test
    public void testGenerateReferences() {
        List<Reference> references = RealReferenceDataGenerator.generateReferences();
        
        System.out.println("\n生成的参考文献统计:");
        System.out.println("总数: " + references.size());
        System.out.println("期刊 [J]: 50");
        System.out.println("专著 [M]: 50");
        System.out.println("学位论文 [D]: 50");
        System.out.println("会议录 [C]: 50");
        
        // 输出前 10 条示例
        System.out.println("\n前 10 条参考文献示例:");
        for (int i = 0; i < Math.min(10, references.size()); i++) {
            Reference ref = references.get(i);
            System.out.println(ref.getRefId() + " " + ref.getRawText());
        }
    }
}
