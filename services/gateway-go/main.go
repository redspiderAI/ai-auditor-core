package main

import (
	"log"
	"net/http"
	"os"

	"github.com/labstack/echo/v4"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/handlers"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/worker"
)

func main() {
	e := echo.New()

	s := store.NewStore()
	tasks := make(chan string, 100)

	// Routes
	e.POST("/api/v1/upload", handlers.UploadHandler(s, tasks))
	e.GET("/api/v1/tasks/:id", handlers.StatusHandler(s))
	e.GET("/api/v1/report/:id", handlers.ReportHandler(s))
	e.GET("/api/v1/download/:id", handlers.DownloadHandler(s))

	// Start worker
	go worker.Worker(tasks, s)

	port := os.Getenv("GATEWAY_PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("gateway-go starting on :%s", port)
	if err := e.Start("0.0.0.0:" + port); err != http.ErrServerClosed {
		log.Fatalf("server error: %v", err)
	}
}
