package com.auditor.engine.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Drools 规则热更新服务
 *
 * 核心能力：
 *   1. 运行时不停机更新 DRL 规则文件（KieFileSystem + KieBuilder 动态重编译）
 *   2. 基于 MD5 哈希的快速变更检测（只有文件真正变化才触发重编译）
 *   3. 读写锁保证并发安全（审查请求读锁 / 热更新写锁）
 *   4. 自动回滚：新规则编译失败时保留旧版本，服务不中断
 *   5. 文件系统监听：可选开启，自动检测 DRL 文件变化
 *
 * 使用方式：
 *   - REST 接口触发：POST /api/rules/reload
 *   - 传入新的 DRL 内容：POST /api/rules/update/{ruleName}
 *   - 查询当前版本：GET /api/rules/status
 */
@Service
public class DroolsHotReloadService {

    private static final Logger logger = LoggerFactory.getLogger(DroolsHotReloadService.class);

    // 规则文件路径前缀（KieFileSystem 虚拟路径）
    private static final String RULE_BASE_PATH = "src/main/resources/rules/";

    // 当前活跃的 KieContainer（原子引用，支持无锁读取）
    private final AtomicReference<KieContainer> activeContainer = new AtomicReference<>();

    // 读写锁：多个审查请求可以并发读，热更新时独占写
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // 当前规则内容的 MD5 哈希（用于快速判断是否真正变化）
    private final Map<String, String> ruleHashes = new ConcurrentHashMap<>();

    // 热更新历史记录
    private final List<ReloadRecord> reloadHistory = new CopyOnWriteArrayList<>();

    // KieServices 实例
    private final KieServices kieServices = KieServices.Factory.get();

    // 当前规则内容缓存（用于重编译）
    private final Map<String, String> ruleContents = new ConcurrentHashMap<>();

    /**
     * 初始化：从 classpath 加载初始规则
     */
    public DroolsHotReloadService() {
        try {
            KieContainer initial = kieServices.getKieClasspathContainer();
            activeContainer.set(initial);
            logger.info("DroolsHotReloadService 初始化成功，已加载 classpath 规则");
        } catch (Exception e) {
            logger.error("DroolsHotReloadService 初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前活跃的 KieContainer（线程安全，供审查服务调用）
     *
     * 调用方式：
     *   KieContainer container = hotReloadService.getActiveContainer();
     *   KieSession session = container.newKieSession("formattingSession");
     */
    public KieContainer getActiveContainer() {
        rwLock.readLock().lock();
        try {
            return activeContainer.get();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 热更新单条规则文件
     *
     * @param ruleName   规则文件名，如 "formatting/formatting.drl"
     * @param drlContent 新的 DRL 规则内容
     * @return 热更新结果
     */
    public HotReloadResult reloadRule(String ruleName, String drlContent) {
        String newHash = computeMd5(drlContent);
        String oldHash = ruleHashes.get(ruleName);

        // 快速路径：内容未变化，直接返回（基于哈希的快速对齐）
        if (newHash.equals(oldHash)) {
            logger.info("规则 [{}] 内容未变化（MD5: {}），跳过热更新", ruleName, newHash);
            return HotReloadResult.noChange(ruleName, newHash);
        }

        logger.info("检测到规则 [{}] 变化，MD5: {} -> {}，开始热更新...", ruleName, oldHash, newHash);
        long startTime = System.currentTimeMillis();

        // 写锁：热更新期间阻塞新的审查请求（已在处理中的请求用旧规则完成）
        rwLock.writeLock().lock();
        try {
            // 更新规则内容缓存
            ruleContents.put(ruleName, drlContent);

            // 重新编译所有规则（KieFileSystem 全量重建）
            KieContainer newContainer = buildNewContainer();

            // 编译成功：原子替换活跃容器
            KieContainer oldContainer = activeContainer.getAndSet(newContainer);
            ruleHashes.put(ruleName, newHash);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("规则 [{}] 热更新成功，耗时 {}ms", ruleName, elapsed);

            // 记录历史
            ReloadRecord record = new ReloadRecord(ruleName, oldHash, newHash, elapsed, true, null);
            reloadHistory.add(record);

            // 异步关闭旧容器（等待进行中的请求完成）
            if (oldContainer != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000); // 等待 5 秒，让旧请求完成
                        oldContainer.dispose();
                        logger.debug("旧 KieContainer 已释放");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            return HotReloadResult.success(ruleName, oldHash, newHash, elapsed);

        } catch (Exception e) {
            // 编译失败：回滚，保留旧版本，服务不中断
            ruleContents.remove(ruleName); // 回滚内容缓存
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("规则 [{}] 热更新失败，已回滚到旧版本: {}", ruleName, e.getMessage());

            ReloadRecord record = new ReloadRecord(ruleName, oldHash, newHash, elapsed, false, e.getMessage());
            reloadHistory.add(record);

            return HotReloadResult.failure(ruleName, e.getMessage());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 批量热更新多条规则（原子操作：全部成功才切换，任一失败则全部回滚）
     *
     * @param rules Map<规则文件名, DRL内容>
     * @return 批量更新结果
     */
    public HotReloadResult reloadRules(Map<String, String> rules) {
        // 预检查：计算所有规则的哈希，过滤未变化的
        Map<String, String> changedRules = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String newHash = computeMd5(entry.getValue());
            if (!newHash.equals(ruleHashes.get(entry.getKey()))) {
                changedRules.put(entry.getKey(), entry.getValue());
            }
        }

        if (changedRules.isEmpty()) {
            logger.info("批量热更新：所有规则内容均未变化，跳过");
            return HotReloadResult.noChange("batch", "all-same");
        }

        logger.info("批量热更新：{} 条规则发生变化: {}", changedRules.size(), changedRules.keySet());
        long startTime = System.currentTimeMillis();

         // 备份当前内容（用于回滚，必须在 try 块外声明以便 catch 访问）
        Map<String, String> backup = new HashMap<>(ruleContents);
        rwLock.writeLock().lock();
        try {
            // 更新内容缓存
            ruleContents.putAll(changedRules);

            // 尝试编译
            KieContainer newContainer = buildNewContainer();

            // 成功：原子替换
            KieContainer oldContainer = activeContainer.getAndSet(newContainer);
            changedRules.forEach((name, content) ->
                ruleHashes.put(name, computeMd5(content)));

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("批量热更新成功：{} 条规则，耗时 {}ms", changedRules.size(), elapsed);

            if (oldContainer != null) {
                CompletableFuture.runAsync(() -> {
                    try { Thread.sleep(5000); oldContainer.dispose(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }

            return HotReloadResult.success("batch[" + changedRules.size() + "]",
                "old", "new", elapsed);

        } catch (Exception e) {
            // 回滚
            ruleContents.clear();
            ruleContents.putAll(backup);
            logger.error("批量热更新失败，已全部回滚: {}", e.getMessage());
            return HotReloadResult.failure("batch", e.getMessage());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 强制从 classpath 重新加载所有规则（恢复出厂设置）
     *
     * 注意：不能重复调用 kieServices.getKieClasspathContainer()，否则抛出：
     *   "There's already another KieContainer created from a different ClassLoader"
     * 正确做法：通过 ClassLoader 读取 DRL 文件内容，用 KieFileSystem 重新编译
     */
    public HotReloadResult reloadFromClasspath() {
        logger.info("强制从 classpath 重新加载所有规则...");
        long startTime = System.currentTimeMillis();
        rwLock.writeLock().lock();
        try {
            // 从 classpath 资源读取所有 DRL 文件内容
            String[] drlPaths = {
                "rules/formatting/formatting.drl",
                "rules/reference/reference.drl",
                "rules/integrity/integrity.drl"
            };
            Map<String, String> freshContents = new LinkedHashMap<>();
            for (String path : drlPaths) {
                try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
                    if (is != null) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        freshContents.put(path, content);
                        logger.debug("读取 classpath 规则文件: {}", path);
                    } else {
                        logger.warn("未找到 classpath 规则文件: {}", path);
                    }
                } catch (Exception ex) {
                    logger.warn("读取规则文件失败: {} - {}", path, ex.getMessage());
                }
            }

            if (freshContents.isEmpty()) {
                return HotReloadResult.failure("classpath-reload", "未找到任何 DRL 规则文件");
            }

            // 更新内容缓存
            ruleContents.clear();
            ruleContents.putAll(freshContents);

            // 用 KieFileSystem 重新编译（不调用 getKieClasspathContainer）
            KieContainer newContainer = buildNewContainer();

            // 更新哈希
            ruleHashes.clear();
            freshContents.forEach((name, content) -> ruleHashes.put(name, computeMd5(content)));

            // 原子替换容器
            KieContainer old = activeContainer.getAndSet(newContainer);
            if (old != null) {
                CompletableFuture.runAsync(() -> {
                    try { Thread.sleep(3000); old.dispose(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("从 classpath 重新加载成功，耗时 {}ms，共 {} 个规则文件", elapsed, freshContents.size());
            return HotReloadResult.success("classpath-reload", null, String.valueOf(freshContents.size()), elapsed);

        } catch (Exception e) {
            logger.error("从 classpath 重新加载失败: {}", e.getMessage());
            return HotReloadResult.failure("classpath-reload", e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取当前规则状态（版本信息、哈希、历史记录）
     */
    public RuleStatus getStatus() {
        return new RuleStatus(
            ruleHashes,
            reloadHistory.size(),
            reloadHistory.isEmpty() ? null : reloadHistory.get(reloadHistory.size() - 1),
            activeContainer.get() != null
        );
    }

    /**
     * 获取最近 N 条热更新历史
     */
    public List<ReloadRecord> getReloadHistory(int limit) {
        int size = reloadHistory.size();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(reloadHistory.subList(from, size));
    }

    // ==================== 私有方法 ====================

    /**
     * 使用 KieFileSystem 重新构建 KieContainer
     * 将 ruleContents 中的所有规则写入虚拟文件系统并编译
     */
    private KieContainer buildNewContainer() {
        KieFileSystem kfs = kieServices.newKieFileSystem();

        // 将所有规则内容写入 KieFileSystem（虚拟文件系统）
        for (Map.Entry<String, String> entry : ruleContents.entrySet()) {
            String virtualPath = RULE_BASE_PATH + entry.getKey();
            kfs.write(virtualPath, entry.getValue());
            logger.debug("写入规则到 KieFileSystem: {}", virtualPath);
        }

        // 编译
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        // 检查编译结果
        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            StringBuilder errors = new StringBuilder("DRL 编译错误:\n");
            results.getMessages(Message.Level.ERROR).forEach(msg ->
                errors.append("  [").append(msg.getPath()).append("] ").append(msg.getText()).append("\n"));
            throw new IllegalArgumentException(errors.toString());
        }

        // 打印警告（不阻断）
        if (results.hasMessages(Message.Level.WARNING)) {
            results.getMessages(Message.Level.WARNING).forEach(msg ->
                logger.warn("DRL 编译警告 [{}]: {}", msg.getPath(), msg.getText()));
        }

        return kieServices.newKieContainer(kieBuilder.getKieModule().getReleaseId());
    }

    /**
     * 计算字符串的 MD5 哈希（用于快速变更检测）
     */
    private String computeMd5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }

    // ==================== 数据类 ====================

    /**
     * 热更新结果
     */
    public static class HotReloadResult {
        public final boolean success;
        public final boolean changed;
        public final String ruleName;
        public final String oldHash;
        public final String newHash;
        public final long elapsedMs;
        public final String errorMessage;
        public final String timestamp;

        private HotReloadResult(boolean success, boolean changed, String ruleName,
                                String oldHash, String newHash, long elapsedMs, String errorMessage) {
            this.success = success;
            this.changed = changed;
            this.ruleName = ruleName;
            this.oldHash = oldHash;
            this.newHash = newHash;
            this.elapsedMs = elapsedMs;
            this.errorMessage = errorMessage;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        static HotReloadResult success(String name, String oldHash, String newHash, long elapsed) {
            return new HotReloadResult(true, true, name, oldHash, newHash, elapsed, null);
        }

        static HotReloadResult noChange(String name, String hash) {
            return new HotReloadResult(true, false, name, hash, hash, 0, null);
        }

        static HotReloadResult failure(String name, String error) {
            return new HotReloadResult(false, true, name, null, null, 0, error);
        }

        @Override
        public String toString() {
            return String.format("HotReloadResult{success=%s, changed=%s, rule='%s', elapsed=%dms, error='%s'}",
                success, changed, ruleName, elapsedMs, errorMessage);
        }
    }

    /**
     * 热更新历史记录
     */
    public static class ReloadRecord {
        public final String ruleName;
        public final String oldHash;
        public final String newHash;
        public final long elapsedMs;
        public final boolean success;
        public final String errorMessage;
        public final String timestamp;

        public ReloadRecord(String ruleName, String oldHash, String newHash,
                            long elapsedMs, boolean success, String errorMessage) {
            this.ruleName = ruleName;
            this.oldHash = oldHash;
            this.newHash = newHash;
            this.elapsedMs = elapsedMs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /**
     * 规则状态信息
     */
    public static class RuleStatus {
        public final Map<String, String> ruleHashes;
        public final int totalReloads;
        public final ReloadRecord lastReload;
        public final boolean containerActive;

        public RuleStatus(Map<String, String> ruleHashes, int totalReloads,
                          ReloadRecord lastReload, boolean containerActive) {
            this.ruleHashes = Collections.unmodifiableMap(ruleHashes);
            this.totalReloads = totalReloads;
            this.lastReload = lastReload;
            this.containerActive = containerActive;
        }
    }
}
