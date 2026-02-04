//go:build debug
// +build debug

package main

import (
	"log"
	"net/http"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/config"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/handlers"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/middleware"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/worker"
)

func main() {
	// Load configuration
	cfg := config.LoadConfig()

	e := echo.New()

	// Set custom error handler
	e.HTTPErrorHandler = middleware.CustomHTTPErrorHandler()

	// Apply default middleware
	for _, m := range middleware.GetDefaultMiddleware() {
		e.Use(m)
	}

	s := store.NewStore()
	tasks := make(chan string, cfg.MaxQueueSize)
	log.Println("Task queue created with capacity:", cfg.MaxQueueSize)

	// Initialize notification service
	notificationSvc := notification.NewNotificationService()

	// Initialize temp file manager
	tempManager := tempmanager.NewTempFileManager(cfg.TempDir)

	// Start the cleanup daemon to remove old temp files
	cleanupInterval := time.Duration(cfg.CleanupIntervalHours) * time.Hour
	maxFileAge := time.Duration(cfg.MaxFileAgeHours) * time.Hour
	tempManager.StartCleanupDaemon(cleanupInterval, maxFileAge)

	// Root endpoint
	e.GET("/", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]interface{}{
			"service":     "AI Auditor Gateway",
			"version":     "1.0.0",
			"description": "Distributed scheduling gateway and delivery center for AI auditing system",
			"endpoints": map[string]string{
				"upload":       "POST /api/v1/upload",
				"batch-upload": "POST /api/v1/batch-upload",
				"status":       "GET /api/v1/tasks/{id}",
				"report":       "GET /api/v1/report/{id}",
				"download":     "GET /api/v1/download/{id}",
				"health":       "GET /health",
				"metrics":      "GET /metrics",
				"websocket":    "GET /ws/task/{id}",
			},
			"status": "running",
		})
	})

	// Routes
	e.POST("/api/v1/audit", handlers.UploadHandler(s, tasks, tempManager))
	e.POST("/api/v1/batch-audit", handlers.BatchUploadHandler(s, tasks, tempManager)) // 新增批量审核接口
	e.GET("/api/v1/tasks/:id", handlers.StatusHandler(s))
	e.GET("/api/v1/report/:id", handlers.ReportHandler(s))
	e.GET("/api/v1/download/:id", handlers.DownloadHandler(s, tempManager))

	// WebSocket route for real-time notifications
	e.GET("/ws/task/:id", func(c echo.Context) error {
		notificationSvc.ServeWs(c.Response(), c.Request(), c.Param("id"))
		return nil
	})

	// Health check endpoint
	e.GET("/health", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{
			"status":  "healthy",
			"service": "gateway-go",
		})
	})

	// Metrics endpoint
	e.GET("/metrics", echo.WrapHandler(promhttp.Handler()))

	// Start worker
	log.Println("Starting worker...")
	go worker.Worker(tasks, s, tempManager, notificationSvc)
	log.Println("Worker started successfully")

	log.Printf("gateway-go starting on :%s", cfg.ServerPort)
	if err := e.Start("0.0.0.0:" + cfg.ServerPort); err != http.ErrServerClosed {
		// Cleanup on shutdown
		tempManager.CleanupAll()
		log.Fatalf("server error: %v", err)
	}
}
