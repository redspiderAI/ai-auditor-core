package report

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/adapter"
)

// ComplianceReport 审查报告结构
type ComplianceReport struct {
	TaskID         string     `json:"task_id"`
	Status         string     `json:"status"`
	GeneratedAt    time.Time  `json:"generated_at"`
	DocumentInfo   DocInfo    `json:"document_info"`
	Issues         []ProtocolIssue    `json:"issues"`
	IssueSummary   Summary    `json:"issue_summary"`
	ComplianceRate float64    `json:"compliance_rate"`
	TotalScore     float64    `json:"total_score"`
}

// DocInfo 文档信息
type DocInfo struct {
	Title     string `json:"title"`
	PageCount int32  `json:"page_count"`
	FileSize  int64  `json:"file_size"`
}

// ProtocolIssue 问题详情 (符合通讯协议)
type ProtocolIssue struct {
	Module      string      `json:"module"`          // 模块类型: structural, relational, semantic
	IssueType   string      `json:"issue_type"`      // 问题类型
	Location    Location    `json:"location"`        // 位置信息
	Description string      `json:"description"`     // 问题描述
	Suggestion  string      `json:"suggestion"`      // 修改建议
	Severity    string      `json:"severity"`        // 严重程度
	Offset      *int32      `json:"offset,omitempty"` // 偏移量（可选）
}

// Location 位置信息
type Location struct {
	SectionID int32 `json:"section_id"`  // 段落ID
	Offset    *int32 `json:"offset,omitempty"` // 偏移量（可选）
}

// Position 位置信息
type Position struct {
	Page   int32 `json:"page"`
	Line   int32 `json:"line"`
	Column int32 `json:"column"`
}

// Summary 问题摘要
type Summary struct {
	TotalCount     int            `json:"total_count"`
	BySeverity     map[string]int `json:"by_severity"`
	ByCategory     map[string]int `json:"by_category"`
	HighRiskIssues int            `json:"high_risk_issues"`
}

// GenerateReport 生成审查报告
func GenerateReport(taskID, sourcePath string, issues []*auditor.Issue, totalScoreImpact float32) (*ComplianceReport, error) {
	// 获取文件信息
	fileInfo, err := os.Stat(sourcePath)
	if err != nil {
		return nil, fmt.Errorf("无法获取文件信息: %w", err)
	}

	// 转换问题列表
	reportIssues := make([]ProtocolIssue, len(issues))
	summary := Summary{
		BySeverity: make(map[string]int),
		ByCategory: make(map[string]int),
	}

	for i, issue := range issues {
		// 确定模块类型
		module := "structural" // 默认为结构模块
		issueType := extractIssueType(issue.Code)

		// 创建位置信息
		location := Location{
			SectionID: issue.SectionId,
		}

		reportIssues[i] = ProtocolIssue{
			Module:      module,
			IssueType:   issueType,
			Location:    location,
			Description: issue.Message,
			Suggestion:  issue.Suggestion,
			Severity:    strings.ToLower(severityToString(issue.Severity)),
		}

		// 更新摘要统计
		severityStr := severityToString(issue.Severity)
		summary.BySeverity[severityStr]++
		summary.ByCategory[extractCategory(issue.Code)]++

		if issue.Severity >= auditor.Severity_HIGH {
			summary.HighRiskIssues++
		}
	}

	summary.TotalCount = len(issues)

	// 计算合规率 (基于问题严重程度)
	complianceRate := calculateComplianceRate(issues)

	report := &ComplianceReport{
		TaskID:      taskID,
		Status:      "completed",
		GeneratedAt: time.Now(),
		DocumentInfo: DocInfo{
			Title:     extractFileName(sourcePath),
			PageCount: 10, // TODO: 从解析结果中获取实际页数
			FileSize:  fileInfo.Size(),
		},
		Issues:         reportIssues,
		IssueSummary:   summary,
		ComplianceRate: complianceRate,
		TotalScore:     float64(totalScoreImpact),
	}

	return report, nil
}

// severityToString 将严重程度枚举转换为字符串
func severityToString(severity auditor.Severity) string {
	switch severity {
	case auditor.Severity_INFO:
		return "INFO"
	case auditor.Severity_LOW:
		return "LOW"
	case auditor.Severity_MEDIUM:
		return "MEDIUM"
	case auditor.Severity_HIGH:
		return "HIGH"
	case auditor.Severity_CRITICAL:
		return "CRITICAL"
	default:
		return "UNKNOWN"
	}
}

// extractCategory 从错误代码中提取类别
func extractCategory(code string) string {
	// 示例：从 "ERR_FONT_001" 中提取 "FONT"
	if len(code) > 4 {
		// 查找第二个下划线的位置
		underscorePos := -1
		count := 0
		for i, r := range code {
			if r == '_' {
				count++
				if count == 2 {
					underscorePos = i
					break
				}
			}
		}
		if underscorePos > 0 {
			return code[4:underscorePos] // 跳过 "ERR_" 前缀
		}
		// 如果只有一个下划线，返回从第4位开始的部分
		return code[4:]
	}
	return "GENERAL"
}

// calculateComplianceRate 计算合规率
func calculateComplianceRate(issues []*auditor.Issue) float64 {
	if len(issues) == 0 {
		return 100.0 // 无问题表示100%合规
	}

	// 根据问题严重程度计算扣分
	var totalDeduction float64
	for _, issue := range issues {
		switch issue.Severity {
		case auditor.Severity_CRITICAL:
			totalDeduction += 10.0
		case auditor.Severity_HIGH:
			totalDeduction += 5.0
		case auditor.Severity_MEDIUM:
			totalDeduction += 2.0
		case auditor.Severity_LOW:
			totalDeduction += 0.5
		}
	}

	// 合规率 = max(0, 100 - 扣分)
	complianceRate := 100.0 - totalDeduction
	if complianceRate < 0 {
		complianceRate = 0
	}

	return complianceRate
}

// extractIssueType 从错误代码中提取问题类型
func extractIssueType(code string) string {
	// 示例：从 "ERR_FONT_001" 中提取 "font_mismatch"
	if len(code) > 4 {
		// 跳过 "ERR_" 前缀，然后查找第一个下划线后的部分
		parts := strings.Split(code[4:], "_")
		if len(parts) > 0 {
			typePart := strings.ToLower(parts[0])
			// 根据类型映射到更友好的名称
			switch typePart {
			case "font":
				return "font_mismatch"
			case "citation":
				return "missing_citation"
			case "typo":
				return "typo"
			case "format":
				return "format_violation"
			default:
				return typePart
			}
		}
	}
	return "general_error"
}

// extractFileName 提取文件名
func extractFileName(path string) string {
	for i := len(path) - 1; i >= 0; i-- {
		if path[i] == '/' || path[i] == '\\' {
			return path[i+1:]
		}
	}
	return path
}

// GenerateReportWithProtocolIssues 生成审查报告（使用协议格式的问题）
func GenerateReportWithProtocolIssues(taskID, sourcePath string, protocolIssues []*adapter.ProtocolIssue, totalScoreImpact float32) (*ComplianceReport, error) {
	// 获取文件信息
	fileInfo, err := os.Stat(sourcePath)
	if err != nil {
		return nil, fmt.Errorf("无法获取文件信息: %w", err)
	}

	// 转换问题列表
	reportIssues := make([]ProtocolIssue, len(protocolIssues))
	summary := Summary{
		BySeverity: make(map[string]int),
		ByCategory: make(map[string]int),
	}

	for i, issue := range protocolIssues {
		reportIssues[i] = ProtocolIssue{
			Module:      issue.Module,
			IssueType:   issue.IssueType,
			Location:    Location{SectionID: issue.Location.SectionID, Offset: issue.Location.Offset},
			Description: issue.Description,
			Suggestion:  issue.Suggestion,
			Severity:    issue.Severity,
			Offset:      issue.Offset,
		}

		// 更新摘要统计
		summary.BySeverity[issue.Severity]++
		summary.ByCategory[issue.IssueType]++

		if isHighRiskSeverity(issue.Severity) {
			summary.HighRiskIssues++
		}
	}

	summary.TotalCount = len(protocolIssues)

	// 计算合规率 (基于问题严重程度)
	complianceRate := calculateComplianceRateFromProtocolIssues(protocolIssues)

	report := &ComplianceReport{
		TaskID:      taskID,
		Status:      "completed",
		GeneratedAt: time.Now(),
		DocumentInfo: DocInfo{
			Title:     extractFileName(sourcePath),
			PageCount: 10, // TODO: 从解析结果中获取实际页数
			FileSize:  fileInfo.Size(),
		},
		Issues:         reportIssues,
		IssueSummary:   summary,
		ComplianceRate: complianceRate,
		TotalScore:     float64(totalScoreImpact),
	}

	return report, nil
}

// isHighRiskSeverity 判断是否为高风险严重程度
func isHighRiskSeverity(severity string) bool {
	highRiskSeverities := map[string]bool{
		"high": true, "critical": true, "HIGH": true, "CRITICAL": true,
	}
	return highRiskSeverities[severity]
}

// calculateComplianceRateFromProtocolIssues 基于协议格式问题计算合规率
func calculateComplianceRateFromProtocolIssues(issues []*adapter.ProtocolIssue) float64 {
	if len(issues) == 0 {
		return 100.0 // 无问题表示100%合规
	}

	// 根据问题严重程度计算扣分
	var totalDeduction float64
	for _, issue := range issues {
		switch issue.Severity {
		case "critical", "CRITICAL":
			totalDeduction += 10.0
		case "high", "HIGH":
			totalDeduction += 5.0
		case "medium", "MEDIUM":
			totalDeduction += 2.0
		case "low", "LOW":
			totalDeduction += 0.5
		}
	}

	// 合规率 = max(0, 100 - 扣分)
	complianceRate := 100.0 - totalDeduction
	if complianceRate < 0 {
		complianceRate = 0
	}

	return complianceRate
}

// SaveReportToFile 将报告保存到文件
func SaveReportToFile(report *ComplianceReport, filePath string) error {
	file, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("无法创建报告文件: %w", err)
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")

	return encoder.Encode(report)
}