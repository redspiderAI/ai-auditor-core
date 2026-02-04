//go:build grpc
// +build grpc

package worker

import (
	"context"
	"fmt"
	"io"
	"log"
	"os"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/workflow"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/report"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/adapter"
)

// WorkerGRPC replaces the simulated worker when built with `-tags grpc`.
func Worker(tasks <-chan string, s *store.Store, tempManager *tempmanager.TempFileManager, notificationSvc *notification.NotificationService) {
	parserAddr := getenvDefault("RUST_PARSER_ADDR", "parser-rs:52051")
	engineAddr := getenvDefault("JAVA_ENGINE_ADDR", "engine-java:9191")
	inferenceAddr := getenvDefault("PY_INFERENCE_ADDR", "inference-py:50051")

	for id := range tasks {
		t, ok := s.GetTask(id)
		if !ok {
			continue
		}

		// Update task status to parsing
		if ok := s.UpdateTask(id, func(t *store.Task) {
			t.Status = store.Parsing
			t.Progress = 10
		}); !ok {
			continue
		}

		// Send notification about status update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Parsing, 10, "Starting document parsing")
		}

		// 使用新的编排器来处理审查流程
		orchestrator := workflow.NewOrchestrator(parserAddr, engineAddr, inferenceAddr)

		ctx, cancel := context.WithTimeout(context.Background(), 120*time.Second) // 增加超时时间
		result, err := orchestrator.Process(ctx, t.SourcePath)
		cancel()

		if err != nil {
			log.Printf("orchestration error for task %s: %v", id, err)
			_ = s.UpdateTask(id, func(t *store.Task) {
				t.Status = store.Error
				t.ErrorMsg = fmt.Sprintf("Orchestration error: %v", err)
			})

			// Send error notification
			if notificationSvc != nil {
				notificationSvc.NotifyTaskError(id, fmt.Sprintf("Orchestration error: %v", err))
			}

			continue
		}

		// 聚合来自B和C的结果，剔除重复项并按section_id排序
		aggregatedResult := orchestrator.AggregateResults(result.AuditResult, result.SemanticResult)

		// Update task status to generating
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = store.Generating
			t.Progress = 80
		})

		// Send notification about progress update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Generating, 80, "Aggregating results and preparing report")
		}

		// Generate outputs
		annotatedPath, reportPath, err := generateOutputs(id, t.SourcePath, aggregatedResult.Issues, aggregatedResult.ScoreImpact, tempManager)
		if err != nil {
			log.Printf("Output generation error for task %s: %v", id, err)
			_ = s.UpdateTask(id, func(t *store.Task) {
				t.Status = store.Error
				t.ErrorMsg = "Report generation error"
			})

			// Send error notification
			if notificationSvc != nil {
				notificationSvc.NotifyTaskError(id, "Report generation error")
			}

			continue
		}

		// Update task status to completed
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.AnnotatedPath = annotatedPath
			t.ReportPath = reportPath
			t.Status = store.Completed
			t.Progress = 100
		})

		// Send completion notification
		if notificationSvc != nil {
			notificationSvc.NotifyTaskCompletion(id)
		}
	}
}

func getenvDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
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
}

func generateOutputs(taskID, sourcePath string, issues []*auditor.Issue, totalScoreImpact float32, tempManager *tempmanager.TempFileManager) (string, string, error) {
	// Generate annotated document path
	annotatedPath := sourcePath + "-annotated.docx"

	// Generate JSON report path
	jsonReportPath := sourcePath + "-report.json"

	// Generate PDF report path
	pdfReportPath := sourcePath + "-report.pdf"

	// Copy source to annotated (in a real system, this would apply annotations)
	err := copyFile(sourcePath, annotatedPath)
	if err != nil {
		return "", "", err
	}

	// Track the generated files
	tempManager.TrackFile(annotatedPath)
	tempManager.TrackFile(jsonReportPath)
	tempManager.TrackFile(pdfReportPath)

	// Convert issues to protocol format
	protocolIssues := adapter.ConvertIssuesToProtocol(issues)

	// Generate comprehensive report using the new report generator
	complianceReport, err := report.GenerateReportWithProtocolIssues(taskID, sourcePath, protocolIssues, totalScoreImpact)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate compliance report: %w", err)
	}

	// Save the JSON report to file
	err = report.SaveReportToFile(complianceReport, jsonReportPath)
	if err != nil {
		return "", "", fmt.Errorf("failed to save JSON report to file: %w", err)
	}

	// Generate and save PDF report
	pdfGen := report.NewPDFGenerator()
	err = pdfGen.GeneratePDFReport(complianceReport, pdfReportPath)
	if err != nil {
		// 如果PDF生成失败，记录警告但不中断流程
		log.Printf("Warning: failed to generate PDF report: %v", err)
	}

	return annotatedPath, jsonReportPath, nil
}