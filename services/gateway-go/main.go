//go:build !debug
// +build !debug

package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/config"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/handlers"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/middleware"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/queue"
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

	// Select queue implementation (Redis Streams preferred, fall back to memory)
	var taskQueue queue.TaskQueue
	if cfg.RedisAddr != "" {
		redisQueue, err := queue.NewRedisStreamQueue(cfg.RedisAddr, cfg.RedisStream, cfg.RedisGroup, cfg.RedisConsumer)
		if err != nil {
			log.Printf("redis queue unavailable, falling back to in-memory queue: %v", err)
		} else {
			taskQueue = redisQueue
			log.Printf("using redis stream queue at %s", cfg.RedisAddr)
		}
	}
	if taskQueue == nil {
		taskQueue = queue.NewInMemoryQueue(cfg.MaxQueueSize)
		log.Printf("using in-memory queue with capacity %d", cfg.MaxQueueSize)
	}

	tasks := make(chan string, cfg.MaxQueueSize)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Pump from abstract queue into worker channel
	go func() {
		for {
			id, err := taskQueue.Dequeue(ctx)
			if err != nil {
				if errors.Is(err, context.Canceled) {
					return
				}
				log.Printf("queue dequeue error: %v", err)
				time.Sleep(time.Second)
				continue
			}
			tasks <- id
		}
	}()

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
	e.POST("/api/v1/audit", handlers.UploadHandler(s, taskQueue, tempManager))
	e.POST("/api/v1/batch-audit", handlers.BatchUploadHandler(s, taskQueue, tempManager)) // 新增批量审核接口
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
	go worker.Worker(tasks, s, tempManager, notificationSvc)

	log.Printf("gateway-go starting on :%s", cfg.ServerPort)
	if err := e.Start("0.0.0.0:" + cfg.ServerPort); err != http.ErrServerClosed {
		// Cleanup on shutdown
		_ = taskQueue.Close()
		tempManager.CleanupAll()
		log.Fatalf("server error: %v", err)
	}

	_ = taskQueue.Close()
}
