package config

import (
	"os"
	"strconv"
)

// Config holds the application configuration
type Config struct {
	ServerPort            string
	TempDir               string
	MaxQueueSize          int
	CleanupIntervalHours  int
	MaxFileAgeHours       int
	LogLevel              string
	RedisAddr             string
	RedisStream           string
	RedisGroup            string
	RedisConsumer         string
	WebhookTimeoutSeconds int
}

// LoadConfig loads configuration from environment variables or uses defaults
func LoadConfig() *Config {
	config := &Config{
		ServerPort:            getEnvOrDefault("GATEWAY_PORT", "8080"),
		TempDir:               getEnvOrDefault("TEMP_DIR", "./temp_docs"),
		MaxQueueSize:          getIntEnvOrDefault("MAX_TASK_QUEUE_SIZE", 100),
		CleanupIntervalHours:  getIntEnvOrDefault("CLEANUP_INTERVAL_HOURS", 1),
		MaxFileAgeHours:       getIntEnvOrDefault("MAX_FILE_AGE_HOURS", 24),
		LogLevel:              getEnvOrDefault("LOG_LEVEL", "info"),
		RedisAddr:             getEnvOrDefault("REDIS_ADDR", ""),
		RedisStream:           getEnvOrDefault("REDIS_STREAM", "audit_tasks"),
		RedisGroup:            getEnvOrDefault("REDIS_GROUP", "gateway_group"),
		RedisConsumer:         getEnvOrDefault("REDIS_CONSUMER", "gateway_consumer"),
		WebhookTimeoutSeconds: getIntEnvOrDefault("WEBHOOK_TIMEOUT_SECONDS", 5),
	}

	return config
}

// getEnvOrDefault returns the environment variable value or the default value
func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// getIntEnvOrDefault returns the integer environment variable value or the default value
func getIntEnvOrDefault(key string, defaultValue int) int {
	if valueStr := os.Getenv(key); valueStr != "" {
		if value, err := strconv.Atoi(valueStr); err == nil {
			return value
		}
	}
	return defaultValue
}
