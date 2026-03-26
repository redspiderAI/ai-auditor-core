package adapter

import (
	"strings"

	auditorpb "github.com/redspiderAI/ai-auditor-core/shared/protos"
)

// ProtocolIssue 符合通讯协议的问题结构
type ProtocolIssue struct {
	Module      string   `json:"module"`          // 模块类型: structural, relational, semantic
	IssueType   string   `json:"issue_type"`      // 问题类型
	Location    Location `json:"location"`        // 位置信息
	Description string   `json:"description"`     // 问题描述
	Suggestion  string   `json:"suggestion"`      // 修改建议
	Severity    string   `json:"severity"`        // 严重程度
	Offset      *int32   `json:"offset,omitempty"` // 偏移量（可选）
}

// Location 位置信息
type Location struct {
	SectionID int32 `json:"section_id"`  // 段落ID
	Offset    *int32 `json:"offset,omitempty"` // 偏移量（可选）
}

// ConvertIssuesToProtocol 将protobuf Issue转换为符合通讯协议的格式
func ConvertIssuesToProtocol(issues []*auditorpb.Issue) []*ProtocolIssue {
	protocolIssues := make([]*ProtocolIssue, len(issues))

	for i, issue := range issues {
		// 确定模块类型
		module := determineModule(issue.Code)

		// 确定问题类型
		issueType := extractIssueType(issue.Code)

		// 创建位置信息
		location := Location{
			SectionID: issue.SectionId,
		}

		// 确定严重程度
		severity := severityToString(issue.Severity)

		protocolIssues[i] = &ProtocolIssue{
			Module:      module,
			IssueType:   issueType,
			Location:    location,
			Description: issue.Message,
			Suggestion:  issue.Suggestion,
			Severity:    severity,
		}
	}

	return protocolIssues
}

// determineModule 根据错误代码确定模块类型
func determineModule(code string) string {
	lowerCode := strings.ToLower(code)

	// 根据错误代码判断模块类型
	if strings.Contains(lowerCode, "semantic") || strings.Contains(lowerCode, "gramma") || strings.Contains(lowerCode, "typo") {
		return "semantic"
	} else if strings.Contains(lowerCode, "cite") || strings.Contains(lowerCode, "refer") {
		return "relational"
	} else {
		// 默认为结构模块
		return "structural"
	}
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
			case "spacing":
				return "spacing_violation"
			case "alignment":
				return "alignment_issue"
			default:
				return typePart
			}
		}
	}
	return "general_error"
}

// severityToString 将严重程度枚举转换为字符串
func severityToString(severity auditorpb.Severity) string {
	switch severity {
	case auditorpb.Severity_INFO:
		return "info"
	case auditorpb.Severity_LOW:
		return "low"
	case auditorpb.Severity_MEDIUM:
		return "medium"
	case auditorpb.Severity_HIGH:
		return "high"
	case auditorpb.Severity_CRITICAL:
		return "critical"
	default:
		return "unknown"
	}
}