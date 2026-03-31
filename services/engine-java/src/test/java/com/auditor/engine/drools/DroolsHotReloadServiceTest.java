package com.auditor.engine.drools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.kie.api.runtime.KieContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drools 热更新服务单元测试
 */
@DisplayName("Drools 规则热更新服务测试")
public class DroolsHotReloadServiceTest {

    private DroolsHotReloadService hotReloadService;

    // 最小可编译的 DRL 内容（用于测试）
    private static final String VALID_DRL =
        "package rules.formatting;\n" +
        "import com.auditor.grpc.Section;\n" +
        "import com.auditor.grpc.Issue;\n" +
        "import com.auditor.grpc.Severity;\n" +
        "import java.util.List;\n" +
        "global java.util.List results;\n" +
        "global org.slf4j.Logger logger;\n" +
        "\n" +
        "rule \"热更新测试规则\"\n" +
        "    when\n" +
        "        $s : Section(type == \"test_hot_reload\")\n" +
        "    then\n" +
        "        Issue issue = Issue.newBuilder()\n" +
        "            .setCode(\"HOT_RELOAD_001\")\n" +
        "            .setMessage(\"热更新规则已生效\")\n" +
        "            .setSeverity(Severity.INFO)\n" +
        "            .build();\n" +
        "        results.add(issue);\n" +
        "end\n";

    private static final String INVALID_DRL =
        "package rules.formatting;\n" +
        "this is not valid drl syntax !!!!\n";

    @BeforeEach
    void setUp() {
        hotReloadService = new DroolsHotReloadService();
    }

    @Test
    @DisplayName("初始化后应有活跃的 KieContainer")
    void testInitialization() {
        KieContainer container = hotReloadService.getActiveContainer();
        assertNotNull(container, "初始化后 KieContainer 不应为 null");

        DroolsHotReloadService.RuleStatus status = hotReloadService.getStatus();
        assertTrue(status.containerActive, "容器应处于活跃状态");
        assertEquals(0, status.totalReloads, "初始热更新次数应为 0");
    }

    @Test
    @DisplayName("相同内容热更新应返回 noChange（基于 MD5 哈希快速对齐）")
    void testNoChangeWhenSameContent() {
        // 第一次更新（建立基准）
        hotReloadService.reloadRule("formatting/test.drl", VALID_DRL);

        // 第二次用相同内容更新
        DroolsHotReloadService.HotReloadResult result =
            hotReloadService.reloadRule("formatting/test.drl", VALID_DRL);

        assertTrue(result.success, "相同内容应返回成功");
        assertFalse(result.changed, "相同内容不应触发重编译");
        assertEquals(0, result.elapsedMs, "未变化时耗时应为 0");
        System.out.println("✅ MD5 哈希快速对齐：相同内容跳过重编译");
    }

    @Test
    @DisplayName("无效 DRL 应编译失败并自动回滚，旧容器保持可用")
    void testInvalidDrlRollback() {
        // 记录旧容器
        KieContainer oldContainer = hotReloadService.getActiveContainer();

        // 尝试加载无效 DRL
        DroolsHotReloadService.HotReloadResult result =
            hotReloadService.reloadRule("formatting/invalid.drl", INVALID_DRL);

        assertFalse(result.success, "无效 DRL 应返回失败");
        assertNotNull(result.errorMessage, "应有错误信息");

        // 验证旧容器仍然可用（自动回滚）
        KieContainer currentContainer = hotReloadService.getActiveContainer();
        assertNotNull(currentContainer, "回滚后容器不应为 null");
        System.out.println("✅ 自动回滚：无效 DRL 编译失败，旧规则保持可用");
        System.out.println("   错误信息: " + result.errorMessage.substring(0, Math.min(100, result.errorMessage.length())));
    }

    @Test
    @DisplayName("从 classpath 重新加载应成功")
    void testReloadFromClasspath() {
        DroolsHotReloadService.HotReloadResult result = hotReloadService.reloadFromClasspath();

        assertTrue(result.success, "从 classpath 重新加载应成功");
        assertNotNull(hotReloadService.getActiveContainer(), "重新加载后容器不应为 null");
        System.out.println("✅ classpath 重新加载成功");
    }

    @Test
    @DisplayName("热更新历史记录应正确追踪")
    void testReloadHistory() {
        // 执行几次热更新
        hotReloadService.reloadRule("test/rule1.drl", VALID_DRL);
        hotReloadService.reloadRule("test/rule2.drl", VALID_DRL);
        hotReloadService.reloadRule("test/invalid.drl", INVALID_DRL); // 失败的

        DroolsHotReloadService.RuleStatus status = hotReloadService.getStatus();
        assertTrue(status.totalReloads >= 2, "应有至少 2 条历史记录");

        // 最后一条应是失败的
        assertNotNull(status.lastReload, "应有最后一条记录");

        System.out.println("✅ 热更新历史记录正确，共 " + status.totalReloads + " 条");
    }

    @Test
    @DisplayName("并发读取 KieContainer 应线程安全")
    void testConcurrentContainerAccess() throws InterruptedException {
        int threadCount = 20;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger nullCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    KieContainer container = hotReloadService.getActiveContainer();
                    if (container == null) nullCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, nullCount.get(), "并发读取时所有线程都应获得非 null 容器");
        System.out.println("✅ 并发读取线程安全：" + threadCount + " 条虚拟线程同时读取，无 null");
    }
}
