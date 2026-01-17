package store

import (
	"encoding/json"
	"os"
	"sync"
)

// Task represents a processing job state.
type Task struct {
	ID            string `json:"id"`
	Status        string `json:"status"`
	Progress      int    `json:"progress"`
	SourcePath    string `json:"source_path"`
	AnnotatedPath string `json:"annotated_path"`
	ReportPath    string `json:"report_path"`
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
