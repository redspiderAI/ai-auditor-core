package handlers

import (
    "bytes"
    "encoding/json"
    "mime/multipart"
    "net/http"
    "net/http/httptest"
    "os"
    "path/filepath"
    "testing"
    "time"

    "github.com/labstack/echo/v4"

    "github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
)

func TestUploadAndStatusHandlers(t *testing.T) {
    // isolate filesystem side effects
    tmpRoot := t.TempDir()
    workDir := filepath.Join(tmpRoot, "wd")
    if err := os.MkdirAll(workDir, 0o755); err != nil {
        t.Fatalf("mkdir workdir: %v", err)
    }
    oldWD, _ := os.Getwd()
    if err := os.Chdir(workDir); err != nil {
        t.Fatalf("chdir: %v", err)
    }
    t.Cleanup(func() { _ = os.Chdir(oldWD) })

    e := echo.New()
    s := store.NewStore()
    tasks := make(chan string, 1)

    // build multipart form with a dummy file
    var body bytes.Buffer
    writer := multipart.NewWriter(&body)
    fw, err := writer.CreateFormFile("file", "sample.docx")
    if err != nil {
        t.Fatalf("create form file: %v", err)
    }
    if _, err := fw.Write([]byte("dummy")); err != nil {
        t.Fatalf("write form file: %v", err)
    }
    writer.Close()

    req := httptest.NewRequest(http.MethodPost, "/api/v1/upload", &body)
    req.Header.Set(echo.HeaderContentType, writer.FormDataContentType())
    rec := httptest.NewRecorder()

    if err := UploadHandler(s, tasks)(e.NewContext(req, rec)); err != nil {
        t.Fatalf("upload handler error: %v", err)
    }
    if rec.Code != http.StatusAccepted {
        t.Fatalf("expected 202, got %d", rec.Code)
    }

    var resp map[string]string
    if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
        t.Fatalf("parse response: %v", err)
    }
    id := resp["task_id"]
    if id == "" {
        t.Fatalf("task_id empty")
    }

    // task should be stored
    if _, ok := s.GetTask(id); !ok {
        t.Fatalf("task not stored")
    }

    // task should be enqueued
    select {
    case got := <-tasks:
        if got != id {
            t.Fatalf("queued id mismatch: %s", got)
        }
    case <-time.After(2 * time.Second):
        t.Fatalf("task not enqueued")
    }

    // status handler should return 200
    req2 := httptest.NewRequest(http.MethodGet, "/api/v1/tasks/"+id, nil)
    rec2 := httptest.NewRecorder()
    ctx2 := e.NewContext(req2, rec2)
    ctx2.SetParamNames("id")
    ctx2.SetParamValues(id)
    if err := StatusHandler(s)(ctx2); err != nil {
        t.Fatalf("status handler error: %v", err)
    }
    if rec2.Code != http.StatusOK {
        t.Fatalf("expected 200, got %d", rec2.Code)
    }
}
