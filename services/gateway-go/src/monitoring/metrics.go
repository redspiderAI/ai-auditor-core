package monitoring

import (
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

// Metrics holds application metrics
type Metrics struct {
	RequestCount    *prometheus.CounterVec
	RequestDuration *prometheus.HistogramVec
	TaskProcessed   prometheus.Counter
	ActiveTasks     prometheus.Gauge
	ErrorCount      *prometheus.CounterVec
}

var (
	instance *Metrics
	once     sync.Once
)

// GetMetrics returns the singleton metrics instance
func GetMetrics() *Metrics {
	once.Do(func() {
		instance = &Metrics{
			RequestCount: promauto.NewCounterVec(
				prometheus.CounterOpts{
					Name: "gateway_requests_total",
					Help: "Total number of requests",
				},
				[]string{"method", "endpoint", "status"},
			),
			RequestDuration: promauto.NewHistogramVec(
				prometheus.HistogramOpts{
					Name: "gateway_request_duration_seconds",
					Help: "Request duration in seconds",
				},
				[]string{"method", "endpoint"},
			),
			TaskProcessed: promauto.NewCounter(
				prometheus.CounterOpts{
					Name: "gateway_tasks_processed_total",
					Help: "Total number of processed tasks",
				},
			),
			ActiveTasks: promauto.NewGauge(
				prometheus.GaugeOpts{
					Name: "gateway_active_tasks",
					Help: "Number of active tasks",
				},
			),
			ErrorCount: promauto.NewCounterVec(
				prometheus.CounterOpts{
					Name: "gateway_errors_total",
					Help: "Total number of errors",
				},
				[]string{"type", "source"},
			),
		}
	})
	return instance
}

// RecordRequest records a request metric
func (m *Metrics) RecordRequest(method, endpoint, status string, duration time.Duration) {
	m.RequestCount.WithLabelValues(method, endpoint, status).Inc()
	m.RequestDuration.WithLabelValues(method, endpoint).Observe(duration.Seconds())
}

// IncTaskProcessed increments the task processed counter
func (m *Metrics) IncTaskProcessed() {
	m.TaskProcessed.Inc()
}

// SetActiveTasks sets the active tasks gauge
func (m *Metrics) SetActiveTasks(count float64) {
	m.ActiveTasks.Set(count)
}

// IncError increments the error counter
func (m *Metrics) IncError(errorType, source string) {
	m.ErrorCount.WithLabelValues(errorType, source).Inc()
}