package handlers

import (
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
)

// UploadHandler handles file uploads and enqueues tasks.
func UploadHandler(s *store.Store, tasks chan<- string) echo.HandlerFunc {
	return func(c echo.Context) error {
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
		_ = os.MkdirAll(tmpDir, 0o755)
		dstPath := filepath.Join(tmpDir, id+".docx")
		dst, err := os.Create(dstPath)
		if err != nil {
			return err
		}
		defer dst.Close()

		if _, err := io.Copy(dst, src); err != nil {
			return err
		}

		s.AddTask(&store.Task{ID: id, Status: "Pending", Progress: 0, SourcePath: dstPath})

		select {
		case tasks <- id:
		default:
			// queue full: mark queued and enqueue asynchronously
			_ = s.UpdateTask(id, func(t *store.Task) { t.Status = "Queued" })
			go func() { tasks <- id }()
		}

		return c.JSON(http.StatusAccepted, map[string]string{"task_id": id})
	}
}

// StatusHandler returns task status.
func StatusHandler(s *store.Store) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.GetTask(id)
		if !ok {
			return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
		}
		return c.JSON(http.StatusOK, t)
	}
}

// ReportHandler returns the JSON report if available.
func ReportHandler(s *store.Store) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.GetTask(id)
		if !ok {
			return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
		}
		if t.Status != "Completed" {
			return c.JSON(http.StatusAccepted, map[string]string{"status": t.Status})
		}
		if t.ReportPath == "" {
			return c.JSON(http.StatusInternalServerError, map[string]string{"error": "report missing"})
		}
		f, err := os.Open(t.ReportPath)
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
}

// DownloadHandler serves the annotated docx or report.
func DownloadHandler(s *store.Store) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.GetTask(id)
		if !ok {
			return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
		}
		if t.Status != "Completed" {
			return c.NoContent(http.StatusAccepted)
		}
		if t.AnnotatedPath != "" {
			return c.File(t.AnnotatedPath)
		}
		if t.ReportPath != "" {
			return c.File(t.ReportPath)
		}
		return c.JSON(http.StatusInternalServerError, map[string]string{"error": "nothing to download"})
	}
}
