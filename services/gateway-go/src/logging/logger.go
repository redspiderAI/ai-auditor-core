package logging

import (
	"os"
	"time"

	"github.com/labstack/gommon/log"
)

// Logger wraps the echo logger with additional functionality
type Logger struct {
	*log.Logger
}

// NewLogger creates a new logger instance
func NewLogger() *Logger {
	logger := log.New("gateway")
	logger.SetHeader("${time_rfc3339} | ${level} | ${short_file}:${line} | ")
	logger.SetOutput(os.Stdout)
	logger.SetLevel(log.INFO)
	
	return &Logger{logger}
}

// LogRequest logs incoming requests
func (l *Logger) LogRequest(method, uri string, startTime time.Time) {
	duration := time.Since(startTime)
	l.Infoj(log.JSON{
		"method":   method,
		"uri":      uri,
		"duration": duration.String(),
		"type":     "request",
	})
}

// LogTaskEvent logs task-related events
func (l *Logger) LogTaskEvent(taskID, eventType, status string, progress int) {
	l.Infoj(log.JSON{
		"task_id":   taskID,
		"event":     eventType,
		"status":    status,
		"progress":  progress,
		"type":      "task_event",
		"timestamp": time.Now().Format(time.RFC3339),
	})
}

// LogError logs error events
func (l *Logger) LogError(operation, errorStr string) {
	l.Errorj(log.JSON{
		"operation": operation,
		"error":     errorStr,
		"type":      "error",
		"timestamp": time.Now().Format(time.RFC3339),
	})
}