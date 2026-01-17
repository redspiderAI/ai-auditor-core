package main

import (
    "io"
    "os"
    "path/filepath"
    "sync"
    "time"
)

// Task represents a processing job
type Task struct {
    ID           string `json:"id"`
    Status       string `json:"status"`
    Progress     int    `json:"progress"`
    SourcePath   string `json:"source_path"`
    AnnotatedPath string `json:"annotated_path"`
    ReportPath   string `json:"report_path"`
}

// Store holds tasks in memory
type Store struct {
    mu    sync.RWMutex
    tasks map[string]*Task
}

func NewStore() *Store {
    return &Store{tasks: make(map[string]*Task)}
}

// Worker consumes task IDs and simulates processing (parse -> audit -> report -> annotate)
func Worker(tasks <-chan string, store *Store) {
    for id := range tasks {
        store.mu.Lock()
        t, ok := store.tasks[id]
        if !ok {
            store.mu.Unlock()
            continue
        }
        t.Status = "Parsing"
        t.Progress = 10
        store.mu.Unlock()

        // simulate parse (in real implementation, call Rust gRPC parse)
        time.Sleep(1 * time.Second)

        store.mu.Lock()
        t.Status = "Auditing"
        t.Progress = 40
        store.mu.Unlock()

        // simulate audit (call Java/Python) with incremental progress
        for p := 50; p <= 90; p += 10 {
            time.Sleep(800 * time.Millisecond)
            store.mu.Lock()
            t.Progress = p
            store.mu.Unlock()
        }

        // produce outputs: copy source to annotated path and create a JSON report
        annotated := filepath.Join("..", "temp_docs", id+"-annotated.docx")
        report := filepath.Join("..", "temp_docs", id+"-report.json")

        // copy file
        if srcFile, err := os.Open(t.SourcePath); err == nil {
            if dstFile, err := os.Create(annotated); err == nil {
                io.Copy(dstFile, srcFile)
                dstFile.Close()
            }
            srcFile.Close()
        }

        // write a simple report
        _ = writeReport(report, map[string]any{
            "task_id": id,
            "status": "completed",
            "generated_at": time.Now().Format(time.RFC3339),
            "issues": []any{},
        })

        store.mu.Lock()
        t.AnnotatedPath = annotated
        t.ReportPath = report
        t.Status = "Completed"
        t.Progress = 100
        store.mu.Unlock()
    }
}
