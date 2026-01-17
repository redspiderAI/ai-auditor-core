//go:build grpc
// +build grpc

package main

import (
    "context"
    "time"
    "os"
    "log"

    "google.golang.org/grpc"

    // NOTE: generated Go proto package must be placed under `shared/protos/go` or a module path
    // that matches your project. Adjust the import path below to your generated package.
    auditorpb "github.com/redspiderAI/ai-auditor-core/shared/protos/go/academic/auditor"
)

// WorkerGRPC replaces the simulated Worker when built with `-tags grpc`.
func Worker(tasks <-chan string, store *Store) {
    parserAddr := os.Getenv("RUST_PARSER_ADDR")
    if parserAddr == "" { parserAddr = "parser-rs:52051" }
    engineAddr := os.Getenv("JAVA_ENGINE_ADDR")
    if engineAddr == "" { engineAddr = "engine-java:9191" }
    inferenceAddr := os.Getenv("PY_INFERENCE_ADDR")
    if inferenceAddr == "" { inferenceAddr = "inference-py:50051" }

    for id := range tasks {
        store.mu.Lock()
        t, ok := store.tasks[id]
        if !ok {
            store.mu.Unlock()
            continue
        }
        t.Status = "Parsing"
        t.Progress = 5
        store.mu.Unlock()

        ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
        // Call parser-rs ParseDocument
        conn, err := grpc.DialContext(ctx, parserAddr, grpc.WithInsecure(), grpc.WithBlock())
        if err != nil {
            log.Printf("failed to dial parser: %v", err)
            store.mu.Lock(); t.Status = "Error: parser connect"; store.mu.Unlock(); cancel(); continue
        }
        client := auditorpb.NewDocumentAuditorClient(conn)
        parseReq := &auditorpb.ParseRequest{FilePath: t.SourcePath}
        parsed, err := client.ParseDocument(ctx, parseReq)
        conn.Close()
        cancel()
        if err != nil {
            log.Printf("parse error: %v", err)
            store.mu.Lock(); t.Status = "Error: parse"; store.mu.Unlock(); continue
        }

        store.mu.Lock(); t.Status = "Auditing"; t.Progress = 40; store.mu.Unlock()

        // Call engine-java AuditRules and inference-py AnalyzeSemantics in parallel
        ctx, cancel = context.WithTimeout(context.Background(), 45*time.Second)
        connEngine, err := grpc.DialContext(ctx, engineAddr, grpc.WithInsecure(), grpc.WithBlock())
        if err != nil { log.Printf("engine dial err: %v", err); store.mu.Lock(); t.Status="Error: engine connect"; store.mu.Unlock(); cancel(); continue }
        engineClient := auditorpb.NewDocumentAuditorClient(connEngine)

        connInf, err := grpc.DialContext(ctx, inferenceAddr, grpc.WithInsecure(), grpc.WithBlock())
        if err != nil { log.Printf("inference dial err: %v", err); store.mu.Lock(); t.Status="Error: inference connect"; store.mu.Unlock(); connEngine.Close(); cancel(); continue }
        infClient := auditorpb.NewDocumentAuditorClient(connInf)

        // AuditRules uses ParsedData
        auditReq := &auditorpb.AuditRequest{Data: parsed}
        semanticReq := &auditorpb.SemanticRequest{Sections: parsed.Sections}

        // parallel calls
        chAudit := make(chan *auditorpb.AuditResponse, 1)
        chSem := make(chan *auditorpb.AuditResponse, 1)

        go func() {
            ctx2, _ := context.WithTimeout(context.Background(), 25*time.Second)
            resp, err := engineClient.AuditRules(ctx2, auditReq)
            if err != nil { log.Printf("AuditRules error: %v", err); chAudit <- &auditorpb.AuditResponse{}; return }
            chAudit <- resp
        }()

        go func() {
            ctx2, _ := context.WithTimeout(context.Background(), 25*time.Second)
            resp, err := infClient.AnalyzeSemantics(ctx2, semanticReq)
            if err != nil { log.Printf("AnalyzeSemantics error: %v", err); chSem <- &auditorpb.AuditResponse{}; return }
            chSem <- resp
        }()

        auditResp := <-chAudit
        semResp := <-chSem

        connEngine.Close()
        connInf.Close()
        cancel()

        // aggregate issues
        issues := append(auditResp.Issues, semResp.Issues...)

        // create annotated copy and report
        annotated := t.SourcePath + "-annotated.docx"
        report := t.SourcePath + "-report.json"

        // TODO: call parser InjectAnnotations RPC (not defined in proto yet) or reuse ParseDocument for reverse mapping
        // For now, just copy source to annotated and write combined report
        _ = copyFile(t.SourcePath, annotated)
        _ = writeReport(report, map[string]any{"task_id": id, "issues": issues})

        store.mu.Lock()
        t.AnnotatedPath = annotated
        t.ReportPath = report
        t.Status = "Completed"
        t.Progress = 100
        store.mu.Unlock()
    }
}

// copyFile helper
func copyFile(src, dst string) error {
    in, err := os.Open(src)
    if err != nil { return err }
    defer in.Close()
    out, err := os.Create(dst)
    if err != nil { return err }
    defer out.Close()
    _, err = io.Copy(out, in)
    return err
}
