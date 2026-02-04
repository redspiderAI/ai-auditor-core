package report

import (
	"fmt"

	"github.com/jung-kurt/gofpdf"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
)

// PDFGenerator PDF报告生成器
type PDFGenerator struct {
	pdf *gofpdf.Fpdf
}

// NewPDFGenerator 创建新的PDF生成器
func NewPDFGenerator() *PDFGenerator {
	pdfInstance := gofpdf.New("P", "mm", "A4", "")
	pdfInstance.AddPage()

	return &PDFGenerator{
		pdf: pdfInstance,
	}
}

// GeneratePDFReport 生成PDF格式的审查报告
func (pg *PDFGenerator) GeneratePDFReport(report *ComplianceReport, outputPath string) error {
	// 设置标题
	pg.pdf.SetFont("Arial", "B", 16)
	pg.pdf.CellFormat(40, 10, "学术文档合规性审查报告", "", 1, "C", false, 0, "")

	// 添加空行
	pg.pdf.Ln(5)

	// 添加文档信息
	pg.pdf.SetFont("Arial", "B", 12)
	pg.pdf.CellFormat(40, 8, "文档信息", "", 1, "", false, 0, "")

	pg.pdf.SetFont("Arial", "", 10)
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("文件名: %s", report.DocumentInfo.Title), "", 1, "", false, 0, "")
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("页数: %d", report.DocumentInfo.PageCount), "", 1, "", false, 0, "")
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("文件大小: %d bytes", report.DocumentInfo.FileSize), "", 1, "", false, 0, "")
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("生成时间: %s", report.GeneratedAt.Format("2006-01-02 15:04:05")), "", 1, "", false, 0, "")

	// 添加空行
	pg.pdf.Ln(5)

	// 添加合规率和评分
	pg.pdf.SetFont("Arial", "B", 12)
	pg.pdf.CellFormat(40, 8, "审查结果", "", 1, "", false, 0, "")

	pg.pdf.SetFont("Arial", "", 10)
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("合规率: %.2f%%", report.ComplianceRate), "", 1, "", false, 0, "")
	pg.pdf.CellFormat(40, 6, fmt.Sprintf("总扣分: %.2f", report.TotalScore), "", 1, "", false, 0, "")

	// 添加空行
	pg.pdf.Ln(5)

	// 添加问题摘要图表
	pg.addSummaryChart(report)

	// 添加空行
	pg.pdf.Ln(5)

	// 添加问题详情
	pg.addIssuesDetail(report)

	// 保存PDF文件
	err := pg.pdf.OutputFileAndClose(outputPath)
	if err != nil {
		return fmt.Errorf("failed to save PDF: %w", err)
	}

	return nil
}

// addSummaryChart 添加问题摘要图表
func (pg *PDFGenerator) addSummaryChart(report *ComplianceReport) {
	pg.pdf.SetFont("Arial", "B", 12)
	pg.pdf.CellFormat(40, 8, "问题摘要", "", 1, "", false, 0, "")

	// 准备数据
	labels := []string{}
	values := []float64{}
	colors := []struct{R, G, B int}{}

	for severity, count := range report.IssueSummary.BySeverity {
		if count > 0 {
			labels = append(labels, severity)
			values = append(values, float64(count))

			// 根据严重程度设置颜色
			switch severity {
			case "CRITICAL":
				colors = append(colors, struct{R, G, B int}{255, 0, 0}) // 红色
			case "HIGH":
				colors = append(colors, struct{R, G, B int}{255, 100, 0}) // 橙色
			case "MEDIUM":
				colors = append(colors, struct{R, G, B int}{255, 200, 0}) // 黄色
			case "LOW":
				colors = append(colors, struct{R, G, B int}{0, 100, 255}) // 蓝色
			default:
				colors = append(colors, struct{R, G, B int}{100, 100, 100}) // 灰色
			}
		}
	}

	if len(values) == 0 {
		pg.pdf.SetFont("Arial", "", 10)
		pg.pdf.CellFormat(40, 6, "无问题记录", "", 1, "", false, 0, "")
		return
	}

	// 计算总值
	var total float64
	for _, v := range values {
		total += v
	}

	if total == 0 {
		return
	}

	// 显示图例（替代饼图，因为gofpdf的饼图实现较复杂）
	pg.pdf.SetFont("Arial", "B", 10)  // 修复：添加缺失的引号
	pg.pdf.CellFormat(40, 6, "问题分布:", "", 1, "", false, 0, "")

	for i, label := range labels {
		v := values[i]
		pg.pdf.SetFillColor(colors[i].R, colors[i].G, colors[i].B)
		pg.pdf.Rect(20, pg.pdf.GetY(), 5, 5, "F") // 颜色块
		pg.pdf.SetXY(26, pg.pdf.GetY())
		pg.pdf.SetFont("Arial", "", 10)
		pg.pdf.CellFormat(60, 5, fmt.Sprintf("%s: %d (%.1f%%)", label, int(v), (v/total)*100), "", 1, "", false, 0, "")
	}

	// 更新Y坐标
	pg.pdf.Ln(10)
}

// addIssuesDetail 添加问题详情
func (pg *PDFGenerator) addIssuesDetail(report *ComplianceReport) {
	pg.pdf.SetFont("Arial", "B", 12)
	pg.pdf.CellFormat(40, 8, "问题详情", "", 1, "", false, 0, "")

	// 设置表格头部
	pg.pdf.SetFont("Arial", "B", 10)
	pg.pdf.SetFillColor(200, 200, 200)
	pg.pdf.CellFormat(15, 6, "编号", "1", 0, "C", true, 0, "")
	pg.pdf.CellFormat(25, 6, "模块", "1", 0, "C", true, 0, "")
	pg.pdf.CellFormat(20, 6, "严重程度", "1", 0, "C", true, 0, "")
	pg.pdf.CellFormat(30, 6, "段落ID", "1", 0, "C", true, 0, "")
	pg.pdf.CellFormat(80, 6, "问题描述", "1", 1, "C", true, 0, "")

	// 填充问题详情
	pg.pdf.SetFont("Arial", "", 9)
	for i, issue := range report.Issues {
		// 设置行颜色交替
		if i%2 == 0 {
			pg.pdf.SetFillColor(245, 245, 245)
		} else {
			pg.pdf.SetFillColor(255, 255, 255)
		}

		pg.pdf.CellFormat(15, 5, fmt.Sprintf("%d", i+1), "LR", 0, "C", true, 0, "")
		pg.pdf.CellFormat(25, 5, issue.Module, "LR", 0, "C", true, 0, "")
		pg.pdf.CellFormat(20, 5, issue.Severity, "LR", 0, "C", true, 0, "")
		pg.pdf.CellFormat(30, 5, fmt.Sprintf("%d", issue.Location.SectionID), "LR", 0, "C", true, 0, "")

		// 截断过长的描述
		desc := issue.Description
		if len(desc) > 60 {
			desc = desc[:60] + "..."
		}
		pg.pdf.CellFormat(80, 5, desc, "LR", 1, "L", true, 0, "")

		// 添加建议（如果空间允许）
		if issue.Suggestion != "" {
			pg.pdf.SetX(pg.pdf.GetX() - 80) // 回到描述列
			pg.pdf.SetFont("Arial", "", 8)
			suggestion := "建议: " + issue.Suggestion
			if len(suggestion) > 60 {
				suggestion = suggestion[:60] + "..."
			}
			pg.pdf.CellFormat(80, 4, suggestion, "LRB", 1, "L", true, 0, "")
			pg.pdf.SetFont("Arial", "", 9)
		}
	}

	// 添加底部边框
	pg.pdf.SetX(10)
	pg.pdf.CellFormat(15, 0, "", "T", 0, "", false, 0, "")
	pg.pdf.CellFormat(25, 0, "", "T", 0, "", false, 0, "")
	pg.pdf.CellFormat(20, 0, "", "T", 0, "", false, 0, "")
	pg.pdf.CellFormat(30, 0, "", "T", 0, "", false, 0, "")
	pg.pdf.CellFormat(80, 0, "", "T", 0, "", false, 0, "")
}

// GenerateDetailedReport 生成详细的审查报告
func GenerateDetailedReport(taskID, sourcePath string, issues []*auditor.Issue, totalScoreImpact float32) (*ComplianceReport, error) {
	return GenerateReport(taskID, sourcePath, issues, totalScoreImpact)
}