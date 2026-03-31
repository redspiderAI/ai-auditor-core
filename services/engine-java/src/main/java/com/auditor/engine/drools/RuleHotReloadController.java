package com.auditor.engine.drools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Drools 规则热更新 REST 控制器
 *
 * 提供以下接口：
 *   GET  /api/rules/status              - 查询当前规则版本和热更新历史
 *   POST /api/rules/reload              - 从 classpath 重新加载所有规则（恢复出厂设置）
 *   POST /api/rules/update/{ruleName}   - 更新单条规则（传入新的 DRL 内容）
 *   POST /api/rules/update/batch        - 批量更新多条规则（原子操作）
 *   GET  /api/rules/history             - 查询最近 20 条热更新历史
 *
 * 典型使用场景（成员 B 修改规则后，无需重启服务）：
 *   1. 修改本地 formatting.drl
 *   2. 调用 POST /api/rules/update/formatting/formatting.drl，传入新内容
 *   3. 服务在 < 500ms 内切换到新规则，正在处理的请求不受影响
 */
@RestController
@RequestMapping("/api/rules")
public class RuleHotReloadController {

    @Autowired
    private DroolsHotReloadService hotReloadService;

    /**
     * 查询当前规则状态
     *
     * 响应示例：
     * {
     *   "containerActive": true,
     *   "totalReloads": 3,
     *   "ruleHashes": {
     *     "formatting/formatting.drl": "a1b2c3d4...",
     *     "reference/reference.drl": "e5f6g7h8..."
     *   },
     *   "lastReload": { "ruleName": "formatting/formatting.drl", "success": true, "elapsedMs": 312 }
     * }
     */
    @GetMapping("/status")
    public ResponseEntity<DroolsHotReloadService.RuleStatus> getStatus() {
        return ResponseEntity.ok(hotReloadService.getStatus());
    }

    /**
     * 从 classpath 重新加载所有规则（恢复出厂设置）
     *
     * 使用场景：规则文件已通过 CI/CD 部署到 jar 包，需要重新加载
     */
    @PostMapping("/reload")
    public ResponseEntity<DroolsHotReloadService.HotReloadResult> reloadFromClasspath() {
        DroolsHotReloadService.HotReloadResult result = hotReloadService.reloadFromClasspath();
        return result.success
            ? ResponseEntity.ok(result)
            : ResponseEntity.internalServerError().body(result);
    }

    /**
     * 更新单条规则文件（运行时热更新）
     *
     * @param ruleName 规则文件名（URL 编码），如 "formatting%2Fformatting.drl"
     * @param body     请求体，包含 "content" 字段（DRL 文件内容）
     *
     * 请求示例：
     *   POST /api/rules/update/formatting%2Fformatting.drl
     *   Content-Type: application/json
     *   { "content": "package rules.formatting;\n\nrule \"检查字体\" ..." }
     *
     * 响应示例（成功）：
     *   { "success": true, "changed": true, "ruleName": "formatting/formatting.drl",
     *     "oldHash": "a1b2...", "newHash": "c3d4...", "elapsedMs": 312 }
     *
     * 响应示例（内容未变化）：
     *   { "success": true, "changed": false, "ruleName": "...", "elapsedMs": 0 }
     *
     * 响应示例（编译失败，自动回滚）：
     *   { "success": false, "errorMessage": "DRL 编译错误: ..." }
     */
    @PostMapping("/update/{ruleName}")
    public ResponseEntity<DroolsHotReloadService.HotReloadResult> updateRule(
            @PathVariable String ruleName,
            @RequestBody Map<String, String> body) {

        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(
                DroolsHotReloadService.HotReloadResult.failure(ruleName, "请求体中缺少 'content' 字段"));
        }

        // URL 解码规则名（支持路径中的 /）
        String decodedName = ruleName.replace("%2F", "/").replace("%2f", "/");

        DroolsHotReloadService.HotReloadResult result = hotReloadService.reloadRule(decodedName, content);
        return result.success
            ? ResponseEntity.ok(result)
            : ResponseEntity.internalServerError().body(result);
    }

    /**
     * 批量更新多条规则（原子操作：全部成功才切换，任一失败则全部回滚）
     *
     * 请求示例：
     *   POST /api/rules/update/batch
     *   Content-Type: application/json
     *   {
     *     "formatting/formatting.drl": "package rules.formatting; ...",
     *     "reference/reference.drl": "package rules.reference; ..."
     *   }
     */
    @PostMapping("/update/batch")
    public ResponseEntity<DroolsHotReloadService.HotReloadResult> updateRulesBatch(
            @RequestBody Map<String, String> rules) {

        if (rules == null || rules.isEmpty()) {
            return ResponseEntity.badRequest().body(
                DroolsHotReloadService.HotReloadResult.failure("batch", "请求体不能为空"));
        }

        DroolsHotReloadService.HotReloadResult result = hotReloadService.reloadRules(rules);
        return result.success
            ? ResponseEntity.ok(result)
            : ResponseEntity.internalServerError().body(result);
    }

    /**
     * 查询最近 20 条热更新历史
     */
    @GetMapping("/history")
    public ResponseEntity<List<DroolsHotReloadService.ReloadRecord>> getHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(hotReloadService.getReloadHistory(Math.min(limit, 100)));
    }
}
