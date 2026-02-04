<<<<<<< HEAD
//go:build !grpc
// +build !grpc

=======
>>>>>>> main
package worker

import (
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
<<<<<<< HEAD
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
)

// Worker simulates processing (parse -> audit -> report -> annotate).
func Worker(tasks <-chan string, s *store.Store, tempManager *tempmanager.TempFileManager, notificationSvc *notification.NotificationService) {
	for id := range tasks {
		// Update task status to parsing
		if ok := s.UpdateTask(id, func(t *store.Task) {
			t.Status = store.Parsing
=======
)

// Worker simulates processing (parse -> audit -> report -> annotate).
func Worker(tasks <-chan string, s *store.Store) {
	for id := range tasks {
		if ok := s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Parsing"
>>>>>>> main
			t.Progress = 10
		}); !ok {
			continue
		}

<<<<<<< HEAD
		// Send notification about status update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Parsing, 10, "Starting document parsing")
		}

		// Simulate parsing phase - convert document to structured format
		time.Sleep(1 * time.Second)

		// Update task status to auditing
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = store.Auditing
			t.Progress = 40
		})

		// Send notification about status update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Auditing, 40, "Performing rule and semantic audits")
		}

		// Simulate audit phase - process with members B and C
		for p := 50; p <= 90; p += 10 {
			time.Sleep(800 * time.Millisecond)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Progress = p })

			// Send progress notification
			if notificationSvc != nil {
				notificationSvc.NotifyTaskUpdate(id, store.Auditing, p, "Processing...")
			}
		}

		// Generate structured parsed data according to protocol
		parsedData := generateParsedData(id)

		// Simulate rule checking (Member B)
		ruleIssues := simulateRuleCheck(parsedData)

		// Simulate semantic analysis (Member C)
		semanticIssues := simulateSemanticAnalysis(parsedData)

		annotated := filepath.Join(tempManager.TempDir, id+"-annotated.docx")
		report := filepath.Join(tempManager.TempDir, id+"-report.json")

		if t, ok := s.GetTask(id); ok {
			_ = copyFile(t.SourcePath, annotated)
			// Track the generated files
			tempManager.TrackFile(annotated)
			tempManager.TrackFile(report)
		}

		// Combine results from both modules
		allIssues := append(ruleIssues, semanticIssues...)

		// Generate detailed report according to protocol
=======
		time.Sleep(1 * time.Second) // simulate parse

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Auditing"
			t.Progress = 40
		})

		for p := 50; p <= 90; p += 10 {
			time.Sleep(800 * time.Millisecond)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Progress = p })
		}

		annotated := filepath.Join("..", "temp_docs", id+"-annotated.docx")
		report := filepath.Join("..", "temp_docs", id+"-report.json")

		if t, ok := s.GetTask(id); ok {
			_ = copyFile(t.SourcePath, annotated)
		}

>>>>>>> main
		_ = store.WriteReport(report, map[string]any{
			"task_id":      id,
			"status":       "completed",
			"generated_at": time.Now().Format(time.RFC3339),
<<<<<<< HEAD
			"document_info": map[string]any{
				"title":      parsedData.Metadata.Title,
				"page_count": parsedData.Metadata.PageCount,
				"file_size":  1234,
			},
			"issues": convertIssuesToProtocol(allIssues),
			"issue_summary": generateIssueSummary(allIssues),
			"compliance_rate": calculateComplianceRate(allIssues),
			"total_score":     calculateTotalScore(allIssues),
=======
			"issues":       []any{},
>>>>>>> main
		})

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.AnnotatedPath = annotated
			t.ReportPath = report
<<<<<<< HEAD
			t.Status = store.Completed
			t.Progress = 100
		})

		// Send completion notification
		if notificationSvc != nil {
			notificationSvc.NotifyTaskCompletion(id)
		}
	}
}

// generateParsedData generates structured document data according to protocol
func generateParsedData(taskID string) *auditor.ParsedData {
	return &auditor.ParsedData{
		DocId: taskID,
		Metadata: &auditor.DocumentMetadata{
			Title:       "基于AI的文档审查研究",
			PageCount:   24,
			MarginTop:   1.0,
			MarginBottom: 1.0,
		},
		Sections: []*auditor.Section{
			{
				SectionId: 1,
				Type:      "heading",
				Level:     1,
				Text:      "1. 引言",
				Props:     map[string]string{"font_size": "16", "bold": "true", "before_spacing": "12"},
			},
			{
				SectionId: 2,
				Type:      "paragraph",
				Level:     0,
				Text:      "随着人工智能的发展，文档审查变得尤为重要[1]。",
				Props:     map[string]string{"first_line_indent": "2.0"},
			},
		},
		References: []*auditor.Reference{
			{
				RefId:        "[1]",
				RawText:      "[1] 张三. 人工智能导论[M]. 北京: 科学出版社, 2023.",
				IsValidFormat: true,
			},
		},
	}
}

// simulateRuleCheck simulates rule checking by Member B
func simulateRuleCheck(parsedData *auditor.ParsedData) []*auditor.Issue {
	return []*auditor.Issue{
		{
			Code:            "ERR_FONT_001",
			Message:         "一级标题字体应为黑体，当前为宋体。",
			SectionId:       1,
			Severity:        auditor.Severity_MEDIUM,
			Suggestion:      "请修改为黑体三号",
			OriginalSnippet: "1. 引言",
		},
		{
			Code:            "ERR_CITATION_001",
			Message:         "正文引用了[1]，但参考文献列表中未找到对应项。",
			SectionId:       2,
			Severity:        auditor.Severity_HIGH,
			Suggestion:      "请补充条目[1]或修改引用编号",
			OriginalSnippet: "随着人工智能的发展，文档审查变得尤为重要[1]。",
		},
	}
}

// simulateSemanticAnalysis simulates semantic analysis by Member C
func simulateSemanticAnalysis(parsedData *auditor.ParsedData) []*auditor.Issue {
	return []*auditor.Issue{
		{
			Code:            "ERR_TYPO_001",
			Message:         "检测到疑似错别字：'份' -> '分'。",
			SectionId:       2,
			Severity:        auditor.Severity_HIGH,
			Suggestion:      "成分",
			OriginalSnippet: "随着人工智能的发展，文档审查变得尤为重要[1]。",
		},
	}
}

// convertIssuesToProtocol converts internal issues to protocol format
func convertIssuesToProtocol(issues []*auditor.Issue) []map[string]any {
	protocolIssues := make([]map[string]any, len(issues))

	for i, issue := range issues {
		var module string
		var issueType string

		switch {
		case contains(issue.Code, "FONT"):
			module = "structural"
			issueType = "font_mismatch"
		case contains(issue.Code, "CITATION"):
			module = "relational"
			issueType = "missing_citation"
		case contains(issue.Code, "TYPO"):
			module = "semantic"
			issueType = "typo"
		default:
			module = "structural"
			issueType = "general_error"
		}

		protocolIssues[i] = map[string]any{
			"module":      module,
			"issue_type":  issueType,
			"location":    map[string]any{"section_id": issue.SectionId},
			"description": issue.Message,
			"suggestion":  issue.Suggestion,
			"severity":    severityToString(issue.Severity),
		}

		// Add offset if present in original snippet
		if issue.OriginalSnippet != "" {
			protocolIssues[i]["location"].(map[string]any)["offset"] = 5
		}
	}

	return protocolIssues
}

// generateIssueSummary generates summary statistics
func generateIssueSummary(issues []*auditor.Issue) map[string]any {
	summary := map[string]any{
		"total_count": len(issues),
		"by_severity": map[string]int{
			"high":   0,
			"medium": 0,
			"low":    0,
		},
		"by_category": map[string]int{
			"structural":  0,
			"relational":  0,
			"semantic":    0,
		},
		"high_risk_issues": 0,
	}

	bySeverity := summary["by_severity"].(map[string]int)
	byCategory := summary["by_category"].(map[string]int)

	for _, issue := range issues {
		severity := severityToString(issue.Severity)
		bySeverity[severity]++

		var category string
		switch {
		case contains(issue.Code, "FONT"):
			category = "structural"
		case contains(issue.Code, "CITATION"):
			category = "relational"
		case contains(issue.Code, "TYPO"):
			category = "semantic"
		default:
			category = "structural"
		}

		byCategory[category]++

		if issue.Severity >= auditor.Severity_HIGH {
			summary["high_risk_issues"] = summary["high_risk_issues"].(int) + 1
		}
	}

	return summary
}

// calculateComplianceRate calculates compliance rate based on issues
func calculateComplianceRate(issues []*auditor.Issue) float64 {
	if len(issues) == 0 {
		return 100.0
	}

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

	rate := 100.0 - totalDeduction
	if rate < 0 {
		return 0.0
	}
	return rate
}

// calculateTotalScore calculates total score impact
func calculateTotalScore(issues []*auditor.Issue) float64 {
	var totalScore float64
	for _, issue := range issues {
		switch issue.Severity {
		case auditor.Severity_CRITICAL:
			totalScore += 10.0
		case auditor.Severity_HIGH:
			totalScore += 5.0
		case auditor.Severity_MEDIUM:
			totalScore += 2.0
		case auditor.Severity_LOW:
			totalScore += 0.5
		}
	}
	return totalScore
}

// Helper functions
func contains(str, substr string) bool {
	return len(str) >= len(substr) &&
		   (str[:len(substr)] == substr ||
		    str[len(str)-len(substr):] == substr ||
			findSubstring(str, substr))
}

func findSubstring(str, substr string) bool {
	if len(substr) == 0 {
		return true
	}
	for i := 0; i <= len(str)-len(substr); i++ {
		if str[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

func severityToString(severity auditor.Severity) string {
	switch severity {
	case auditor.Severity_INFO:
		return "info"
	case auditor.Severity_LOW:
		return "low"
	case auditor.Severity_MEDIUM:
		return "medium"
	case auditor.Severity_HIGH:
		return "high"
	case auditor.Severity_CRITICAL:
		return "critical"
	default:
		return "unknown"
=======
			t.Status = "Completed"
			t.Progress = 100
		})
>>>>>>> main
	}
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
<<<<<<< HEAD
}
=======
}
>>>>>>> main
