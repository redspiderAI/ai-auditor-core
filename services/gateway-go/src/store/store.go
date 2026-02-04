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
	Pending     TaskStatus = "Pending"
	Queued      TaskStatus = "Queued"
	Parsing     TaskStatus = "Parsing"
	Auditing    TaskStatus = "Auditing"
	Generating  TaskStatus = "Generating"
	Completed   TaskStatus = "Completed"
	Error       TaskStatus = "Error"
)

// Task represents a processing job state.
type Task struct {
	ID            string     `json:"id"`
	Status        TaskStatus `json:"status"`
	Progress      int        `json:"progress"`
	SourcePath    string     `json:"source_path"`
	AnnotatedPath string     `json:"annotated_path"`
	ReportPath    string     `json:"report_path"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	ErrorMsg      string     `json:"error_msg,omitempty"`
}

// Store keeps tasks in memory with simple locking.
type Store struct {
	mu    sync.RWMutex
	tasks map[string]*Task
}

// NewStore constructs an empty task store.
func NewStore() *Store {
	return &Store{tasks: make(map[string]*Task)}
}

// AddTask inserts a task.
func (s *Store) AddTask(t *Task) {
	s.mu.Lock()
	defer s.mu.Unlock()
	t.CreatedAt = time.Now()
	t.UpdatedAt = time.Now()
	s.tasks[t.ID] = t
}

// GetTask returns a task by ID.
func (s *Store) GetTask(id string) (*Task, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	t, ok := s.tasks[id]
	return t, ok
}

// UpdateTask applies a mutation if the task exists.
func (s *Store) UpdateTask(id string, fn func(*Task)) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.tasks[id]
	if !ok {
		return false
	}
	fn(t)
	t.UpdatedAt = time.Now()
	return true
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