//go:build grpc
// +build grpc

package worker

import (
	"context"
	"io"
	"log"
	"os"
	"time"

	"google.golang.org/grpc"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"

	// NOTE: adjust to your generated Go proto package path.
	auditorpb "github.com/redspiderAI/ai-auditor-core/shared/protos/go/academic/auditor"
)

// WorkerGRPC replaces the simulated worker when built with `-tags grpc`.
func Worker(tasks <-chan string, s *store.Store) {
	parserAddr := getenvDefault("RUST_PARSER_ADDR", "parser-rs:52051")
	engineAddr := getenvDefault("JAVA_ENGINE_ADDR", "engine-java:9191")
	inferenceAddr := getenvDefault("PY_INFERENCE_ADDR", "inference-py:50051")

	for id := range tasks {
		t, ok := s.GetTask(id)
		if !ok {
			continue
		}
		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Parsing"
			t.Progress = 5
		})

		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		conn, err := grpc.DialContext(ctx, parserAddr, grpc.WithInsecure(), grpc.WithBlock())
		if err != nil {
			log.Printf("failed to dial parser: %v", err)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Status = "Error: parser connect" })
			cancel()
			continue
		}
		client := auditorpb.NewDocumentAuditorClient(conn)
		parsed, err := client.ParseDocument(ctx, &auditorpb.ParseRequest{FilePath: t.SourcePath})
		conn.Close()
		cancel()
		if err != nil {
			log.Printf("parse error: %v", err)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Status = "Error: parse" })
			continue
		}

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Auditing"
			t.Progress = 40
		})

		ctx, cancel = context.WithTimeout(context.Background(), 45*time.Second)
		connEngine, err := grpc.DialContext(ctx, engineAddr, grpc.WithInsecure(), grpc.WithBlock())
		if err != nil {
			log.Printf("engine dial err: %v", err)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Status = "Error: engine connect" })
			cancel()
			continue
		}
		engineClient := auditorpb.NewDocumentAuditorClient(connEngine)

		connInf, err := grpc.DialContext(ctx, inferenceAddr, grpc.WithInsecure(), grpc.WithBlock())
		if err != nil {
			log.Printf("inference dial err: %v", err)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Status = "Error: inference connect" })
			connEngine.Close()
			cancel()
			continue
		}
		infClient := auditorpb.NewDocumentAuditorClient(connInf)

		auditReq := &auditorpb.AuditRequest{Data: parsed}
		semanticReq := &auditorpb.SemanticRequest{Sections: parsed.Sections}

		chAudit := make(chan *auditorpb.AuditResponse, 1)
		chSem := make(chan *auditorpb.AuditResponse, 1)

		go func() {
			ctx2, _ := context.WithTimeout(context.Background(), 25*time.Second)
			resp, err := engineClient.AuditRules(ctx2, auditReq)
			if err != nil {
				log.Printf("AuditRules error: %v", err)
				chAudit <- &auditorpb.AuditResponse{}
				return
			}
			chAudit <- resp
		}()

		go func() {
			ctx2, _ := context.WithTimeout(context.Background(), 25*time.Second)
			resp, err := infClient.AnalyzeSemantics(ctx2, semanticReq)
			if err != nil {
				log.Printf("AnalyzeSemantics error: %v", err)
				chSem <- &auditorpb.AuditResponse{}
				return
			}
			chSem <- resp
		}()

		auditResp := <-chAudit
		semResp := <-chSem

		connEngine.Close()
		connInf.Close()
		cancel()

		issues := append(auditResp.Issues, semResp.Issues...)
		annotated := t.SourcePath + "-annotated.docx"
		report := t.SourcePath + "-report.json"

		_ = copyFile(t.SourcePath, annotated)
		_ = store.WriteReport(report, map[string]any{"task_id": id, "issues": issues})

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.AnnotatedPath = annotated
			t.ReportPath = report
			t.Status = "Completed"
			t.Progress = 100
		})
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
