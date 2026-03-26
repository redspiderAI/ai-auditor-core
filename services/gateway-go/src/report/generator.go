package report

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/jung-kurt/gofpdf"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
)

// ComplianceReport represents the structure of a compliance report
type ComplianceReport struct {
	TaskID         string                 `json:"task_id"`
	Status         string                 `json:"status"`
	GeneratedAt    string                 `json:"generated_at"`
	DocumentInfo   map[string]interface{} `json:"document_info"`
	Issues         []map[string]interface{} `json:"issues"`
	IssueSummary   map[string]interface{} `json:"issue_summary"`
	ComplianceRate float64                `json:"compliance_rate"`
	TotalScore     float64                `json:"total_score"`
}

// ReportGenerator handles the creation of various report formats
type ReportGenerator struct{}

// NewReportGenerator creates a new report generator instance
func NewReportGenerator() *ReportGenerator {
	return &ReportGenerator{}
}

// GenerateJSONReport generates a JSON report
func (rg *ReportGenerator) GenerateJSONReport(task *store.Task) ([]byte, error) {
	report := ComplianceReport{
		TaskID:         task.ID,
		Status:         string(task.Status),
		GeneratedAt:    time.Now().Format(time.RFC3339),
		DocumentInfo:   map[string]interface{}{"source_path": task.SourcePath},
		Issues:         []map[string]interface{}{}, // Placeholder - would come from audit results
		IssueSummary:   map[string]interface{}{"total_count": 0},
		ComplianceRate: 100.0, // Placeholder
		TotalScore:     0.0,   // Placeholder
	}

	jsonData, err := json.MarshalIndent(report, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to marshal JSON report: %w", err)
	}

	return jsonData, nil
}

// GeneratePDFReport generates a PDF report using gofpdf
func (rg *ReportGenerator) GeneratePDFReport(task *store.Task, outputPath string) error {
	pdf := gofpdf.New("P", "mm", "A4", "")
	pdf.AddPage()
	pdf.SetFont("Arial", "B", 16)
	pdf.Cell(40, 10, fmt.Sprintf("Audit Report for Task: %s", task.ID))
	pdf.Ln(12)

	pdf.SetFont("Arial", "", 12)
	pdf.Cell(40, 10, fmt.Sprintf("Status: %s", task.Status))
	pdf.Ln(8)
	pdf.Cell(40, 10, fmt.Sprintf("Created: %s", task.CreatedAt.Format("2006-01-02 15:04:05")))
	pdf.Ln(8)
	pdf.Cell(40, 10, fmt.Sprintf("Progress: %d%%", task.Progress))
	pdf.Ln(12)

	// Add a simple table for issues if available
	if task.ReportPath != "" {
		// If there's a report file, we could parse it and add details
		pdf.SetFont("Arial", "B", 14)
		pdf.Cell(40, 10, "Audit Summary")
		pdf.Ln(8)
		pdf.SetFont("Arial", "", 12)
		pdf.Cell(40, 10, "This report was generated based on document compliance analysis.")
		pdf.Ln(8)
		pdf.Cell(40, 10, "For detailed findings, please refer to the JSON report.")
		pdf.Ln(12)
	}

	// Add compliance rate if available
	pdf.SetFont("Arial", "B", 12)
	pdf.Cell(40, 10, "Compliance Rate:")
	pdf.SetFont("Arial", "", 12)
	pdf.Cell(40, 10, "N/A") // Would come from actual audit results
	pdf.Ln(20)

	// Footer
	pdf.SetY(-15)
	pdf.SetFont("Arial", "I", 8)
	pdf.CellFormat(0, 10, fmt.Sprintf("Generated on %s", time.Now().Format("2006-01-02 15:04:05")), 
		"", 0, "C", false, 0, "")

	// Write the PDF to file
	err := pdf.OutputFileAndClose(outputPath)
	if err != nil {
		return fmt.Errorf("failed to write PDF report: %w", err)
	}

	return nil
}

// SaveReportToFile saves the report to the specified file path
func (rg *ReportGenerator) SaveReportToFile(report *ComplianceReport, filePath string) error {
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

// GenerateComprehensiveReport generates all required report formats
func (rg *ReportGenerator) GenerateComprehensiveReport(task *store.Task, outputDir string) (map[string]string, error) {
	paths := make(map[string]string)
	
	// Generate JSON report
	jsonPath := filepath.Join(outputDir, fmt.Sprintf("%s-report.json", task.ID))
	jsonData, err := rg.GenerateJSONReport(task)
	if err != nil {
		return nil, fmt.Errorf("failed to generate JSON report: %w", err)
	}
	
	err = os.WriteFile(jsonPath, jsonData, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to save JSON report: %w", err)
	}
	paths["json"] = jsonPath
	
	// Generate PDF report
	pdfPath := filepath.Join(outputDir, fmt.Sprintf("%s-report.pdf", task.ID))
	err = rg.GeneratePDFReport(task, pdfPath)
	if err != nil {
		return nil, fmt.Errorf("failed to generate PDF report: %w", err)
	}
	paths["pdf"] = pdfPath

	return paths, nil
}