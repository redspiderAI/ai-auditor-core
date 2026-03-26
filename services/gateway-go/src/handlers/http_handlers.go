package handlers

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/queue"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
)

// UploadHandler handles file uploads and enqueues tasks.
func UploadHandler(s *store.Store, taskQueue queue.TaskQueue, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
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
		tmpDir := tempManager.TempDir
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

		// Track the uploaded file
		tempManager.TrackFile(dstPath)

		callbackURL := c.FormValue("callback_url")
		s.AddTask(&store.Task{ID: id, Status: store.Pending, Progress: 0, SourcePath: dstPath, CallbackURL: callbackURL})

		if err := taskQueue.Enqueue(c.Request().Context(), id); err != nil {
			return c.JSON(http.StatusServiceUnavailable, map[string]string{"error": "queue unavailable", "detail": err.Error()})
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
		if t.Status != store.Completed {
			return c.JSON(http.StatusAccepted, map[string]string{"status": string(t.Status)})
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

// DownloadHandler serves a ZIP archive containing the annotated docx and report.
func DownloadHandler(s *store.Store, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.GetTask(id)
		if !ok {
			return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
		}
		if t.Status != store.Completed {
			return c.NoContent(http.StatusAccepted)
		}

		// Create a ZIP archive in memory
		zipBuffer := new(bytes.Buffer)
		zipWriter := zip.NewWriter(zipBuffer)
		defer zipWriter.Close()

		// Add annotated document to ZIP if it exists
		if t.AnnotatedPath != "" {
			file, err := os.Open(t.AnnotatedPath)
			if err == nil {
				defer file.Close()

				writer, err := zipWriter.Create("annotated.docx")
				if err == nil {
					io.Copy(writer, file)
				}
			}
		}

		// Add report to ZIP if it exists
		if t.ReportPath != "" {
			file, err := os.Open(t.ReportPath)
			if err == nil {
				defer file.Close()

				writer, err := zipWriter.Create("report.json")
				if err == nil {
					io.Copy(writer, file)
				}
			}
		}

		// Close the zip writer to finalize the archive
		zipWriter.Close()

		// Clean up temp files after download
		defer func() {
			go func() {
				// Small delay to ensure file is served
				time.Sleep(1 * time.Second)
				tempManager.CleanupTaskFiles(id)
			}()
		}()

		// Return the ZIP file
		c.Response().Header().Set("Content-Type", "application/zip")
		c.Response().Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=\"%s_result.zip\"", id))
		return c.Blob(http.StatusOK, "application/zip", zipBuffer.Bytes())
	}
}

// BatchUploadHandler handles batch file uploads and enqueues tasks.
func BatchUploadHandler(s *store.Store, taskQueue queue.TaskQueue, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
	return func(c echo.Context) error {
		form, err := c.MultipartForm()
		if err != nil {
			return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid multipart form"})
		}

		files := form.File["files"]
		if len(files) == 0 {
			return c.JSON(http.StatusBadRequest, map[string]string{"error": "no files provided"})
		}

		if len(files) > 10 { // 限制批量上传文件数量
			return c.JSON(http.StatusBadRequest, map[string]string{"error": "too many files, maximum 10 allowed"})
		}

		var results []map[string]interface{}
		successCount := 0
		callbackURL := c.FormValue("callback_url")

		for _, fileHeader := range files {
			id := uuid.New().String()
			tmpDir := tempManager.TempDir
			_ = os.MkdirAll(tmpDir, 0o755)
			dstPath := filepath.Join(tmpDir, id+".docx")

			src, err := fileHeader.Open()
			if err != nil {
				results = append(results, map[string]interface{}{
					"filename": fileHeader.Filename,
					"success":  false,
					"error":    err.Error(),
				})
				continue
			}
			defer src.Close()

			dst, err := os.Create(dstPath)
			if err != nil {
				results = append(results, map[string]interface{}{
					"filename": fileHeader.Filename,
					"success":  false,
					"error":    err.Error(),
				})
				continue
			}
			defer dst.Close()

			if _, err := io.Copy(dst, src); err != nil {
				results = append(results, map[string]interface{}{
					"filename": fileHeader.Filename,
					"success":  false,
					"error":    err.Error(),
				})
				continue
			}

			// Track the uploaded file
			tempManager.TrackFile(dstPath)

			s.AddTask(&store.Task{ID: id, Status: store.Pending, Progress: 0, SourcePath: dstPath, CallbackURL: callbackURL})
			if err := taskQueue.Enqueue(c.Request().Context(), id); err != nil {
				results = append(results, map[string]interface{}{
					"filename": fileHeader.Filename,
					"success":  false,
					"error":    fmt.Sprintf("queue unavailable: %v", err),
				})
				continue
			}

			results = append(results, map[string]interface{}{
				"filename": fileHeader.Filename,
				"task_id":  id,
				"success":  true,
			})
			successCount++
		}

		response := map[string]interface{}{
			"total_files":   len(files),
			"success_count": successCount,
			"failure_count": len(files) - successCount,
			"results":       results,
		}

		return c.JSON(http.StatusAccepted, response)
	}
}
