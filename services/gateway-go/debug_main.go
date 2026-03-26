//go:build debug
// +build debug

package main

import (
	"fmt"
	"log"
	"os"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/config"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/handlers"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/middleware"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/notification"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/queue"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/tempmanager"
)

func main() {
	// 显式设置环境变量
	os.Setenv("EMERGENCY_MODE", "true")
	os.Setenv("QUICK_AUDIT_URL", "http://localhost:8123/v1/quick-audit")
	os.Setenv("GATEWAY_PORT", "8080")

	fmt.Println("Environment variables set:")
	fmt.Printf("- EMERGENCY_MODE: %s\n", os.Getenv("EMERGENCY_MODE"))
	fmt.Printf("- QUICK_AUDIT_URL: %s\n", os.Getenv("QUICK_AUDIT_URL"))
	fmt.Printf("- GATEWAY_PORT: %s\n", os.Getenv("GATEWAY_PORT"))

	// Load configuration
	cfg := config.LoadConfig()
	fmt.Printf("\nLoaded config:\n")
	fmt.Printf("- ServerPort: %s\n", cfg.ServerPort)
	fmt.Printf("- TempDir: %s\n", cfg.TempDir)

	// 创建Echo实例
	e := middleware.SetupEcho()

	// 初始化存储
	s := store.NewStore()

	// 选择队列实现
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

	// 初始化通知服务
	notificationSvc := notification.NewNotificationService()

	// 初始化临时文件管理器
	tempManager := tempmanager.NewTempFileManager(cfg.TempDir)

	// 启动清理守护程序以删除旧的临时文件
	cleanupInterval := time.Duration(cfg.CleanupIntervalHours) * time.Hour
	maxFileAge := time.Duration(cfg.MaxFileAgeHours) * time.Hour
	tempManager.StartCleanupDaemon(cleanupInterval, maxFileAge)

	// 注册路由
	handlers.RegisterRoutes(e, s, taskQueue, tempManager, notificationSvc)

	// 启动工作器（但不启动队列消费，因为我们只做测试）
	// go worker.Worker(tasks, s, tempManager, notificationSvc)

	log.Printf("gateway-go starting on :%s", cfg.ServerPort)
	if err := e.Start("0.0.0.0:" + cfg.ServerPort); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
