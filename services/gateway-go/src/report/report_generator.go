package report

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/jung-kurt/gofpdf"
)

// GenerateReportWithProtocolIssues generates a comprehensive report with protocol-formatted issues
func GenerateReportWithProtocolIssues(taskID, sourcePath string, protocolIssues []map[string]interface{}, totalScoreImpact float32) (*ComplianceReport, error) {
	// Calculate compliance rate based on issues
	complianceRate := calculateComplianceRate(len(protocolIssues), totalScoreImpact)

	report := &ComplianceReport{
		TaskID:      taskID,
		Status:      "completed",
		GeneratedAt: time.Now().Format(time.RFC3339),
		DocumentInfo: map[string]interface{}{
			"source_path": sourcePath,
			"file_name":   filepath.Base(sourcePath),
		},
		Issues:         protocolIssues,
		IssueSummary:   generateIssueSummary(protocolIssues),
		ComplianceRate: complianceRate,
		TotalScore:     float64(totalScoreImpact),
	}

	return report, nil
}

// SaveReportToFile saves the report to the specified file path
func SaveReportToFile(report *ComplianceReport, filePath string) error {
	data, err := json.MarshalIndent(report, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal report: %w", err)
	}

	err = os.WriteFile(filePath, data, 0644)
	if err != nil {
		return fmt.Errorf("failed to write report to file: %w", err)
	}

	return nil
}

// calculateComplianceRate calculates compliance rate based on issues
func calculateComplianceRate(issueCount int, totalScoreImpact float32) float64 {
	if issueCount == 0 {
		return 100.0
	}

	// Base calculation: more issues and higher score impact = lower compliance
	baseRate := 100.0 - float64(totalScoreImpact)
	if baseRate < 0 {
		return 0.0
	}
	return baseRate
}

// generateIssueSummary generates summary statistics
func generateIssueSummary(issues []map[string]interface{}) map[string]interface{} {
	summary := map[string]interface{}{
		"total_count": len(issues),
		"by_severity": map[string]int{
			"high":   0,
			"medium": 0,
			"low":    0,
		},
		"by_category": map[string]int{
			"structural": 0,
			"relational": 0,
			"semantic":   0,
		},
		"high_risk_issues": 0,
	}

	bySeverity := summary["by_severity"].(map[string]int)
	byCategory := summary["by_category"].(map[string]int)

	for _, issue := range issues {
		if severity, ok := issue["severity"].(string); ok {
			bySeverity[severity]++
		}

		if category, ok := issue["module"].(string); ok {
			byCategory[category]++
		}

		// Count high risk issues
		if severity, ok := issue["severity"].(string); ok {
			if severity == "high" || severity == "critical" {
				summary["high_risk_issues"] = summary["high_risk_issues"].(int) + 1
			}
		}
	}

	return summary
}

// PDFGenerator handles PDF report generation
type PDFGenerator struct{}

// NewPDFGenerator creates a new PDF generator instance
func NewPDFGenerator() *PDFGenerator {
	return &PDFGenerator{}
}

// GeneratePDFReport generates a PDF report from the compliance report
func (pg *PDFGenerator) GeneratePDFReport(report *ComplianceReport, outputPath string) error {
	pdf := gofpdf.New("P", "mm", "A4", "")
	pdf.AddPage()
	
	// Title
	pdf.SetFont("Arial", "B", 18)
	pdf.Cell(40, 10, fmt.Sprintf("AI Audit Compliance Report - Task %s", report.TaskID))
	pdf.Ln(12)

	// Basic info
	pdf.SetFont("Arial", "", 12)
	pdf.Cell(40, 8, fmt.Sprintf("Status: %s", report.Status))
	pdf.Ln(6)
	pdf.Cell(40, 8, fmt.Sprintf("Generated: %s", report.GeneratedAt))
	pdf.Ln(6)
	pdf.Cell(40, 8, fmt.Sprintf("Document: %s", report.DocumentInfo["file_name"]))
	pdf.Ln(10)

	// Compliance rate
	pdf.SetFont("Arial", "B", 14)
	pdf.Cell(40, 8, "Compliance Summary")
	pdf.Ln(8)
	pdf.SetFont("Arial", "", 12)
	pdf.Cell(40, 6, fmt.Sprintf("Overall Compliance Rate: %.2f%%", report.ComplianceRate))
	pdf.Ln(6)
	pdf.Cell(40, 6, fmt.Sprintf("Total Issues Found: %d", report.IssueSummary["total_count"].(int)))
	pdf.Ln(6)
	pdf.Cell(40, 6, fmt.Sprintf("High Risk Issues: %d", report.IssueSummary["high_risk_issues"].(int)))
	pdf.Ln(12)

	// Issue breakdown
	pdf.SetFont("Arial", "B", 12)
	pdf.Cell(40, 8, "Issue Breakdown")
	pdf.Ln(8)
	
	// Severity breakdown
	bySeverity := report.IssueSummary["by_severity"].(map[string]int)
	pdf.SetFont("Arial", "", 10)
	pdf.Cell(50, 5, fmt.Sprintf("High Severity: %d", bySeverity["high"]))
	pdf.Ln(5)
	pdf.Cell(50, 5, fmt.Sprintf("Medium Severity: %d", bySeverity["medium"]))
	pdf.Ln(5)
	pdf.Cell(50, 5, fmt.Sprintf("Low Severity: %d", bySeverity["low"]))
	pdf.Ln(8)

	// Category breakdown
	byCategory := report.IssueSummary["by_category"].(map[string]int)
	pdf.Cell(50, 5, fmt.Sprintf("Structural Issues: %d", byCategory["structural"]))
	pdf.Ln(5)
	pdf.Cell(50, 5, fmt.Sprintf("Relational Issues: %d", byCategory["relational"]))
	pdf.Ln(5)
	pdf.Cell(50, 5, fmt.Sprintf("Semantic Issues: %d", byCategory["semantic"]))
	pdf.Ln(12)

	// Issues list header
	pdf.SetFont("Arial", "B", 12)
	pdf.Cell(40, 8, "Detailed Issues")
	pdf.Ln(8)
	
	// Add issues to the report
	pdf.SetFont("Arial", "", 10)
	for i, issue := range report.Issues {
		if i >= 10 { // Limit to first 10 issues to prevent overly long reports
			pdf.Cell(40, 5, "... (truncated)")
			break
		}
		
		description := ""
		if desc, ok := issue["description"]; ok {
			description = fmt.Sprintf("%v", desc)
		}
		
		severity := ""
		if sev, ok := issue["severity"]; ok {
			severity = fmt.Sprintf("%v", sev)
		}
		
		location := ""
		if loc, ok := issue["location"]; ok {
			location = fmt.Sprintf("%v", loc)
		}
		
		pdf.Cell(40, 5, fmt.Sprintf("[%s] %s", severity, description))
		pdf.Ln(4)
		pdf.Cell(10, 5, "") // Indent
		pdf.Cell(40, 5, fmt.Sprintf("Location: %s", location))
		pdf.Ln(6)
	}

	// Footer
	pdf.SetY(-20)
	pdf.SetFont("Arial", "I", 8)
	pdf.CellFormat(0, 10, fmt.Sprintf("Generated by AI Audit System on %s", 
		time.Now().Format("2006-01-02 15:04:05")), 
		"", 0, "C", false, 0, "")

	// Write the PDF to file
	err := pdf.OutputFileAndClose(outputPath)
	if err != nil {
		return fmt.Errorf("failed to write PDF report: %w", err)
	}

	return nil
}