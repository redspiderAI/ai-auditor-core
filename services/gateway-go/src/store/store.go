package store

import (
	"encoding/json"
	"os"
	"sync"
	"time"
)

// TaskStatus represents the possible states of a task
type TaskStatus string

const (
	Pending    TaskStatus = "Pending"
	Queued     TaskStatus = "Queued"
	Parsing    TaskStatus = "Parsing"
	Auditing   TaskStatus = "Auditing"
	Generating TaskStatus = "Generating"
	Completed  TaskStatus = "Completed"
	Error      TaskStatus = "Error"
)

// Task represents a processing job state.
type Task struct {
	ID            string     `json:"id"`
	Status        TaskStatus `json:"status"`
	Progress      int        `json:"progress"`
	SourcePath    string     `json:"source_path"`
	AnnotatedPath string     `json:"annotated_path"`
	ReportPath    string     `json:"report_path"`
	PDFReportPath string     `json:"pdf_report_path,omitempty"`
	CallbackURL   string     `json:"callback_url,omitempty"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	ErrorMsg      string     `json:"error_msg,omitempty"`
}

// MemoryStore 内存存储实现
type MemoryStore struct {
	tasks map[string]*Task
	mu    sync.RWMutex
}

// NewMemoryStore 创建新的内存存储实例
func NewMemoryStore() *MemoryStore {
	return &MemoryStore{
		tasks: make(map[string]*Task),
	}
}

// Save 保存任务
func (m *MemoryStore) Save(task *Task) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := time.Now()
	if task.CreatedAt.IsZero() {
		task.CreatedAt = now
	}
	task.UpdatedAt = now
	m.tasks[task.ID] = task
	return nil
}

// Get 获取任务
func (m *MemoryStore) Get(taskID string) (*Task, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	task, exists := m.tasks[taskID]
	return task, exists
}

// Update 更新任务
func (m *MemoryStore) Update(taskID string, updater func(*Task)) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if task, exists := m.tasks[taskID]; exists {
		updater(task)
		task.UpdatedAt = time.Now()
		return nil
	}
	return nil
}

// Delete 删除任务
func (m *MemoryStore) Delete(taskID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.tasks, taskID)
	return nil
}

// WriteReport writes a JSON report to disk.
func WriteReport(path string, data any) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	enc := json.NewEncoder(f)
	enc.SetIndent("", "  ")
	return enc.Encode(data)
}
