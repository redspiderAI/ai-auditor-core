# 如何查看和理解测试结果

## 1. 任务状态响应
当您查询任务状态时，会收到如下响应：
```json
{
  "id": "abc123",
  "status": "Completed",        # 任务状态：Pending, Parsing, Auditing, Generating, Completed, Error
  "progress": 100,              # 完成百分比
  "source_path": "...",         # 源文件路径
  "annotated_path": "...",      # 带注释的文档路径
  "report_path": "...",         # 报告文件路径
  "created_at": "...",          # 创建时间
  "updated_at": "..."           # 更新时间
}
```

## 2. 审查报告结构
审查报告包含以下关键信息：
```json
{
  "task_id": "abc123",
  "status": "completed",
  "generated_at": "...",        # 报告生成时间
  "document_info": {            # 文档基本信息
    "title": "文档标题",
    "page_count": 10,           # 页数
    "file_size": 12345          # 文件大小
  },
  "issues": [                   # 发现的问题列表
    {
      "code": "ERR_CODE_001",   # 错误代码
      "message": "...",         # 错误描述
      "section_id": 5,          # 相关段落ID
      "severity": "HIGH",       # 严重程度：INFO, LOW, MEDIUM, HIGH, CRITICAL
      "suggestion": "...",      # 修复建议
      "original_snippet": "..." # 原始文本片段
    }
  ],
  "issue_summary": {            # 问题摘要
    "total_count": 3,           # 总问题数
    "by_severity": {            # 按严重程度分类
      "HIGH": 1,
      "MEDIUM": 2
    },
    "by_category": {            # 按类别分类
      "CITATION": 2,
      "FORMATTING": 1
    },
    "high_risk_issues": 1       # 高风险问题数
  },
  "compliance_rate": 85.5,      # 合规率 (0-100)
  "total_score": 15.0           # 总扣分数
}
```

## 3. 如何解读结果

### 合规率 (compliance_rate)
- 100% = 完全合规
- 90-99% = 高度合规，有少量轻微问题
- 80-89% = 基本合规，有一些中等问题
- 70-79% = 合规性一般，有较多问题
- <70% = 合规性较差，需要大量修正

### 问题严重程度
- CRITICAL: 严重影响文档质量的问题
- HIGH: 重要问题，需要立即修正
- MEDIUM: 中等问题，建议修正
- LOW: 轻微问题，可选择性修正
- INFO: 仅供参考的信息

### 问题类别
- CITATION: 引用格式问题
- FORMATTING: 格式问题
- SEMANTIC: 语义问题
- STRUCTURE: 结构问题
- STYLE: 风格问题

## 4. 常见测试结果示例

### 示例1：高度合规文档
```json
{
  "compliance_rate": 95.0,
  "issue_summary": {
    "total_count": 2,
    "by_severity": {"LOW": 2},
    "by_category": {"FORMATTING": 2}
  }
}
```
解读：文档质量很高，只有2个轻微格式问题。

### 示例2：需要改进的文档
```json
{
  "compliance_rate": 75.0,
  "issue_summary": {
    "total_count": 8,
    "by_severity": {"HIGH": 2, "MEDIUM": 4, "LOW": 2},
    "by_category": {"CITATION": 5, "FORMATTING": 3}
  }
}
```
解读：文档有较多问题，特别是引用格式需要修正。

## 5. 验证测试成功的标志

- [ ] 任务状态变为"Completed"
- [ ] 返回有效的JSON格式报告
- [ ] 合规率数值合理（通常>70%）
- [ ] 问题列表包含有意义的条目
- [ ] 生成了带注释的文档
- [ ] 生成了PDF格式的报告
- [ ] 服务响应时间合理（通常<60秒）

## 6. 故障排查

如果测试失败，请检查：
- 网关服务是否正在运行
- 依赖服务（A、B、C模块）是否可用
- 网络连接是否正常
- 文件格式是否支持
- 日志中是否有错误信息