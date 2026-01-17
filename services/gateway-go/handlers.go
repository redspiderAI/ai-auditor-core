package main

import (
    "encoding/json"
    "io"
    "net/http"
    "os"
    "path/filepath"
    "time"

    "github.com/google/uuid"
    "github.com/labstack/echo/v4"
)

// UploadHandler handles file uploads and creates a task
func (s *Store) UploadHandler(tasks chan<- string) echo.HandlerFunc {
    return func(c echo.Context) error {
        // single file field `file`
        f, err := c.FormFile("file")
        if err != nil {
            return c.JSON(http.StatusBadRequest, map[string]string{"error": "file required"})
        }

        src, err := f.Open()
        if err != nil {
            return err
        }
        defer src.Close()

        id := uuid.New().String()
        tmpDir := filepath.Join("..", "temp_docs")
        os.MkdirAll(tmpDir, 0o755)
        dstPath := filepath.Join(tmpDir, id+".docx")
        dst, err := os.Create(dstPath)
        if err != nil {
            return err
        }
        defer dst.Close()

        if _, err := io.Copy(dst, src); err != nil {
            return err
        }

        s.mu.Lock()
        s.tasks[id] = &Task{ID: id, Status: "Pending", Progress: 0, SourcePath: dstPath}
        s.mu.Unlock()

        // enqueue
        select {
        case tasks <- id:
        default:
            // queue full
            s.mu.Lock()
            s.tasks[id].Status = "Queued"
            s.mu.Unlock()
            go func() { tasks <- id }()
        }

        return c.JSON(http.StatusAccepted, map[string]string{"task_id": id})
    }
}

// StatusHandler returns task status
func (s *Store) StatusHandler(c echo.Context) error {
    id := c.Param("id")
    s.mu.RLock()
    t, ok := s.tasks[id]
    s.mu.RUnlock()
    if !ok {
        return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
    }
    return c.JSON(http.StatusOK, t)
}

// ReportHandler returns a JSON report (stub)
func (s *Store) ReportHandler(c echo.Context) error {
    id := c.Param("id")
    s.mu.RLock()
    t, ok := s.tasks[id]
    s.mu.RUnlock()
    if !ok {
        return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
    }

    // If completed, look for report file
    if t.Status != "Completed" {
        return c.JSON(http.StatusAccepted, map[string]string{"status": t.Status})
    }

    reportPath := t.ReportPath
    if reportPath == "" {
        return c.JSON(http.StatusInternalServerError, map[string]string{"error": "report missing"})
    }

    f, err := os.Open(reportPath)
    if err != nil {
        return c.JSON(http.StatusInternalServerError, map[string]string{"error": "cannot open report"})
    }
    defer f.Close()

    var buf map[string]any
    if err := json.NewDecoder(f).Decode(&buf); err != nil {
        return c.JSON(http.StatusInternalServerError, map[string]string{"error": "invalid report"})
    }
    return c.JSON(http.StatusOK, buf)
}

// DownloadHandler serves the annotated docx or report bundle
func (s *Store) DownloadHandler(c echo.Context) error {
    id := c.Param("id")
    s.mu.RLock()
    t, ok := s.tasks[id]
    s.mu.RUnlock()
    if !ok {
        return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
    }

    if t.Status != "Completed" {
        return c.NoContent(http.StatusAccepted)
    }

    // serve annotated docx if exists
    if t.AnnotatedPath != "" {
        return c.File(t.AnnotatedPath)
    }

    // otherwise serve report
    if t.ReportPath != "" {
        return c.File(t.ReportPath)
    }

    return c.JSON(http.StatusInternalServerError, map[string]string{"error": "nothing to download"})
}

// helper to write a JSON report
func writeReport(path string, data any) error {
    f, err := os.Create(path)
    if err != nil {
        return err
    }
    defer f.Close()
    enc := json.NewEncoder(f)
    enc.SetIndent("", "  ")
    return enc.Encode(data)
}

// small timestamp helper
func nowStr() string { return time.Now().Format(time.RFC3339) }
