//go:build grpc
// +build grpc

package worker

import (
	"context"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/adapter"
	auditorpb "github.com/redspiderAI/ai-auditor-core/shared/protos"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/report"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/utils"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/workflow"
)

// WorkerGRPC replaces the simulated worker when built with `-tags grpc`.
func Worker(tasks <-chan string, s store.TaskStore, tempManager *tempmanager.TempFileManager, notificationSvc *notification.NotificationService) {
	parserAddr := getenvDefault("RUST_PARSER_ADDR", "parser-rs:52051")
	engineAddr := getenvDefault("JAVA_ENGINE_ADDR", "engine-java:9191")
	inferenceAddr := getenvDefault("PY_INFERENCE_ADDR", "inference-py:50051")
	emergencyMode := strings.EqualFold(os.Getenv("EMERGENCY_MODE"), "true")
	quickAuditURL := getenvDefault("QUICK_AUDIT_URL", "http://localhost:8123/v1/quick-audit")

	// Debug logging
	log.Printf("EMERGENCY_MODE: %s", os.Getenv("EMERGENCY_MODE"))
	log.Printf("emergencyMode flag: %t", emergencyMode)
	log.Printf("quickAuditURL: %s", quickAuditURL)

	for id := range tasks {
		t, ok := s.GetTask(id)
		if !ok {
			continue
		}

		// Update task status to parsing
		if err := s.Update(id, func(t *store.Task) {
			t.Status = store.Parsing
			t.Progress = 10
		}); err != nil {
			continue
		}

		// Send notification about status update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Parsing, 10, "Starting document parsing")
		}

		// 紧急模式：跳过 Rust/Java，直接访问 Python 快速审计接口
		if emergencyMode {
			log.Printf("Entering emergency mode for task %s, using URL: %s", id, quickAuditURL)
			if ok := s.UpdateTask(id, func(t *store.Task) {
				t.Status = store.Auditing
				t.Progress = 50
			}); !ok {
				continue
			}

			fileData, readErr := os.ReadFile(t.SourcePath)
			if readErr != nil {
				log.Printf("emergency read error for task %s: %v", id, readErr)
				_ = s.UpdateTask(id, func(t *store.Task) {
					t.Status = store.Error
					t.ErrorMsg = fmt.Sprintf("Read file failed: %v", readErr)
				})
				if notificationSvc != nil {
					notificationSvc.NotifyTaskError(id, fmt.Sprintf("Read file failed: %v", readErr))
				}
				triggerWebhook(id, t.CallbackURL, store.Error, 0, fmt.Sprintf("Read file failed: %v", readErr))
				continue
			}

			ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
			respBody, callErr := utils.EmergencyFastTrack(ctx, quickAuditURL, filepath.Base(t.SourcePath), fileData)
			cancel()
			if callErr != nil {
				log.Printf("emergency fast track error for task %s: %v", id, callErr)
				// 即使Python服务调用失败，也要确保worker继续运行
				// 创建一个包含错误信息的报告
				errorReport := fmt.Sprintf(`{
					"task_id": "%s",
					"status": "ERROR",
					"summary": {
						"total_issues": 0,
						"score": 0,
						"mode": "EMERGENCY_AI_ONLY",
						"message": "Failed to connect to Python AI service: %s"
					},
					"issues": []
				}`, id, callErr.Error())

				reportPath := t.SourcePath + "-quick-report.json"
				writeErr := os.WriteFile(reportPath, []byte(errorReport), 0o644)
				if writeErr != nil {
					log.Printf("cannot write error report for task %s: %v", id, writeErr)
					_ = s.UpdateTask(id, func(t *store.Task) {
						t.Status = store.Error
						t.ErrorMsg = fmt.Sprintf("Write error report failed: %v", writeErr)
					})
					if notificationSvc != nil {
						notificationSvc.NotifyTaskError(id, fmt.Sprintf("Write error report failed: %v", writeErr))
					}
					triggerWebhook(id, t.CallbackURL, store.Error, 0, fmt.Sprintf("Write error report failed: %v", writeErr))
					continue
				}

				if tempManager != nil {
					tempManager.TrackFile(reportPath)
				}

				_ = s.UpdateTask(id, func(t *store.Task) {
					t.Status = store.Error  // 标记为错误状态，但仍然完成
					t.Progress = 100
					t.ReportPath = reportPath
					t.ErrorMsg = fmt.Sprintf("Python service connection failed: %v", callErr)
				})

				if notificationSvc != nil {
					notificationSvc.NotifyTaskError(id, fmt.Sprintf("Python service connection failed: %v", callErr))
				}
				triggerWebhook(id, t.CallbackURL, store.Error, 100, fmt.Sprintf("Python service connection failed: %v", callErr))
				continue
			}

			reportPath := t.SourcePath + "-quick-report.json"
			if writeErr := os.WriteFile(reportPath, respBody, 0o644); writeErr != nil {
				log.Printf("cannot write quick report for task %s: %v", id, writeErr)
				_ = s.UpdateTask(id, func(t *store.Task) {
					t.Status = store.Error
					t.ErrorMsg = fmt.Sprintf("Write quick report failed: %v", writeErr)
				})
				if notificationSvc != nil {
					notificationSvc.NotifyTaskError(id, fmt.Sprintf("Write quick report failed: %v", writeErr))
				}
				triggerWebhook(id, t.CallbackURL, store.Error, 0, fmt.Sprintf("Write quick report failed: %v", writeErr))
				continue
			}

			if tempManager != nil {
				tempManager.TrackFile(reportPath)
			}

			_ = s.UpdateTask(id, func(t *store.Task) {
				t.Status = store.Completed
				t.Progress = 100
				t.ReportPath = reportPath
			})

			if notificationSvc != nil {
				notificationSvc.NotifyTaskCompletion(id)
			}
			triggerWebhook(id, t.CallbackURL, store.Completed, 100, "Emergency fast track completed")
			continue
		}

		log.Printf("Using regular mode for task %s, not emergency mode", id)
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
			triggerWebhook(id, t.CallbackURL, store.Error, 0, fmt.Sprintf("Orchestration error: %v", err))

			continue
		}

		// 聚合来自B和C的结果，剔除重复项并按section_id排序
		aggregatedResult := orchestrator.AggregateResults(result.AuditResult, result.SemanticResult)

		// 回写批注，生成带批注文档
		annotatedPath, annotateErr := orchestrator.AnnotateDocument(context.Background(), t.SourcePath, aggregatedResult.Issues)
		if annotateErr != nil {
			log.Printf("annotation error for task %s: %v", id, annotateErr)
		}

		// Update task status to generating
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = store.Generating
			t.Progress = 80
		})

		// Send notification about progress update
		if notificationSvc != nil {
			notificationSvc.NotifyTaskUpdate(id, store.Generating, 80, "Aggregating results and preparing report")
		}

		// Generate outputs（如果回写成功则复用回写后的路径）
		annotatedPath, reportPath, pdfReportPath, err := generateOutputs(id, t.SourcePath, annotatedPath, aggregatedResult.Issues, aggregatedResult.ScoreImpact, tempManager)
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
			triggerWebhook(id, t.CallbackURL, store.Error, 0, "Report generation error")

			continue
		}

		// Update task status to completed
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.AnnotatedPath = annotatedPath
			t.ReportPath = reportPath
			t.PDFReportPath = pdfReportPath
			t.Status = store.Completed
			t.Progress = 100
		})

		// Send completion notification
		if notificationSvc != nil {
			notificationSvc.NotifyTaskCompletion(id)
		}
		triggerWebhook(id, t.CallbackURL, store.Completed, 100, "Task completed")
	}
}

func triggerWebhook(taskID, callbackURL string, status store.TaskStatus, progress int, message string) {
	if callbackURL == "" {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		payload := notification.WebhookPayload{TaskID: taskID, Status: string(status), Progress: progress, Message: message}
		if err := notification.SendWebhook(ctx, callbackURL, payload, 5*time.Second); err != nil {
			log.Printf("webhook error: %v", err)
		}
	}()
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

func generateOutputs(taskID, sourcePath, annotatedPath string, issues []*auditorpb.Issue, totalScoreImpact float32, tempManager *tempmanager.TempFileManager) (string, string, string, error) {
	// Generate annotated document path
	if annotatedPath == "" {
		annotatedPath = sourcePath + "-annotated.docx"
	}

	// Generate JSON report path
	jsonReportPath := sourcePath + "-report.json"

	// Generate PDF report path
	pdfReportPath := sourcePath + "-report.pdf"

	// If annotated file does not exist (回写失败或未触发)，回退为原文复制
	if _, statErr := os.Stat(annotatedPath); statErr != nil {
		if err := copyFile(sourcePath, annotatedPath); err != nil {
			return "", "", "", err
		}
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
		return "", "", "", fmt.Errorf("failed to generate compliance report: %w", err)
	}

	// Save the JSON report to file
	err = report.SaveReportToFile(complianceReport, jsonReportPath)
	if err != nil {
		return "", "", "", fmt.Errorf("failed to save JSON report to file: %w", err)
	}

	// Generate and save PDF report
	pdfGen := report.NewPDFGenerator()
	err = pdfGen.GeneratePDFReport(complianceReport, pdfReportPath)
	if err != nil {
		// 如果PDF生成失败，记录警告但不中断流程
		log.Printf("Warning: failed to generate PDF report: %v", err)
	} else {
		// 如果PDF生成成功，也跟踪该文件
		tempManager.TrackFile(pdfReportPath)
	}

	return annotatedPath, jsonReportPath, pdfReportPath, nil
}
