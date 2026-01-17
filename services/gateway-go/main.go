package main

import (
    "log"
    "net/http"
    "os"

    "github.com/labstack/echo/v4"
)

func main() {
    e := echo.New()

    store := NewStore()
    tasks := make(chan string, 100)

    // Routes
    e.POST("/api/v1/upload", store.UploadHandler(tasks))
    e.GET("/api/v1/tasks/:id", store.StatusHandler)
    e.GET("/api/v1/report/:id", store.ReportHandler)
    e.GET("/api/v1/download/:id", store.DownloadHandler)

    // Start worker
    go Worker(tasks, store)

    port := os.Getenv("GATEWAY_PORT")
    if port == "" {
        port = "8080"
    }

    log.Printf("gateway-go starting on :%s", port)
    if err := e.Start("0.0.0.0:" + port); err != http.ErrServerClosed {
        log.Fatalf("server error: %v", err)
    }
}
