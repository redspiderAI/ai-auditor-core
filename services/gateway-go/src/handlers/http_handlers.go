package handlers

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/queue"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
)

// UploadHandler handles file uploads and enqueues tasks.
func UploadHandler(s store.TaskStore, taskQueue queue.TaskQueue, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
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
		task := &store.Task{
			ID:          id,
			Status:      store.Pending,
			Progress:    0,
			SourcePath:  dstPath,
			CallbackURL: callbackURL,
			CreatedAt:   time.Now(),
			UpdatedAt:   time.Now(),
		}
		
		if err := s.Save(task); err != nil {
			return c.JSON(http.StatusInternalServerError, map[string]string{"error": "failed to save task"})
		}

		if err := taskQueue.Enqueue(c.Request().Context(), id); err != nil {
			return c.JSON(http.StatusServiceUnavailable, map[string]string{"error": "queue unavailable", "detail": err.Error()})
		}

		return c.JSON(http.StatusAccepted, map[string]string{"task_id": id})
	}
}

// StatusHandler returns task status.
func StatusHandler(s store.TaskStore) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.Get(id)
		if !ok {
			return c.JSON(http.StatusNotFound, map[string]string{"error": "task not found"})
		}
		return c.JSON(http.StatusOK, t)
	}
}

// ReportHandler returns the JSON report if available.
func ReportHandler(s store.TaskStore) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.Get(id)
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

// DownloadHandler serves a ZIP archive containing the annotated docx, PDF report, and JSON report.
func DownloadHandler(s store.TaskStore, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
	return func(c echo.Context) error {
		id := c.Param("id")
		t, ok := s.Get(id)
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
		if t.AnnotatedPath != "" && fileExists(t.AnnotatedPath) {
			file, err := os.Open(t.AnnotatedPath)
			if err == nil {
				defer file.Close()

				writer, err := zipWriter.Create("annotated.docx")
				if err == nil {
					_, err := io.Copy(writer, file)
					if err != nil {
						log.Printf("Error copying annotated.docx to zip: %v", err)
					}
				}
			} else {
				log.Printf("Error opening annotated file %s: %v", t.AnnotatedPath, err)
			}
		}

		// Add JSON report to ZIP if it exists
		if t.ReportPath != "" && fileExists(t.ReportPath) {
			file, err := os.Open(t.ReportPath)
			if err == nil {
				defer file.Close()

				writer, err := zipWriter.Create("report.json")
				if err == nil {
					_, err := io.Copy(writer, file)
					if err != nil {
						log.Printf("Error copying report.json to zip: %v", err)
					}
				}
			} else {
				log.Printf("Error opening report file %s: %v", t.ReportPath, err)
			}
		}

		// Add PDF report to ZIP if it exists
		if t.PDFReportPath != "" && fileExists(t.PDFReportPath) {
			// Use the PDF report path stored in the task
			file, err := os.Open(t.PDFReportPath)
			if err == nil {
				defer file.Close()

				writer, err := zipWriter.Create("report.pdf")
				if err == nil {
					_, err := io.Copy(writer, file)
					if err != nil {
						log.Printf("Error copying report.pdf to zip: %v", err)
					}
				}
			} else {
				log.Printf("Error opening PDF report file %s: %v", t.PDFReportPath, err)
			}
		} else if t.ReportPath != "" {
			// Fallback: derive PDF path from JSON report path
			pdfReportPath := strings.TrimSuffix(t.ReportPath, ".json") + ".pdf"
			if fileExists(pdfReportPath) {
				// PDF exists, add it to the ZIP
				file, err := os.Open(pdfReportPath)
				if err == nil {
					defer file.Close()

					writer, err := zipWriter.Create("report.pdf")
					if err == nil {
						_, err := io.Copy(writer, file)
						if err != nil {
							log.Printf("Error copying report.pdf to zip: %v", err)
						}
					}
				} else {
					log.Printf("Error opening PDF report file %s: %v", pdfReportPath, err)
				}
			} else {
				log.Printf("PDF report file does not exist: %s", pdfReportPath)
			}
		}

		// Close the zip writer to finalize the archive
		if err := zipWriter.Close(); err != nil {
			log.Printf("Error closing zip writer: %v", err)
			return c.JSON(http.StatusInternalServerError, map[string]string{"error": "failed to create zip archive"})
		}

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

// Helper function to check if a file exists
func fileExists(filename string) bool {
	_, err := os.Stat(filename)
	return err == nil
}

// BatchUploadHandler handles batch file uploads and enqueues tasks.
func BatchUploadHandler(s store.TaskStore, taskQueue queue.TaskQueue, tempManager *tempmanager.TempFileManager) echo.HandlerFunc {
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

			task := &store.Task{
				ID:          id,
				Status:      store.Pending,
				Progress:    0,
				SourcePath:  dstPath,
				CallbackURL: callbackURL,
				CreatedAt:   time.Now(),
				UpdatedAt:   time.Now(),
			}
			
			if err := s.Save(task); err != nil {
				results = append(results, map[string]interface{}{
					"filename": fileHeader.Filename,
					"success":  false,
					"error":    "failed to save task",
				})
				continue
			}
			
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
