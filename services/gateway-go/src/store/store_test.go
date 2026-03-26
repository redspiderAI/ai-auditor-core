package store

import (
	"testing"
	"time"
)

func TestStoreOperations(t *testing.T) {
	s := NewMemoryStore()

	// Test adding a task
	task := &Task{
		ID:       "test-id",
		Status:   Pending,
		Progress: 0,
	}
	s.Save(task)

	// Test getting a task
	retrievedTask, exists := s.Get("test-id")
	if !exists {
		t.Fatal("Task should exist")
	}
	if retrievedTask.ID != "test-id" {
		t.Errorf("Expected ID 'test-id', got '%s'", retrievedTask.ID)
	}

	// Test updating a task
	err := s.Update("test-id", func(t *Task) {
		t.Status = Completed
		t.Progress = 100
	})
	if err != nil {
		t.Error("Task should have been updated")
	}

	updatedTask, _ := s.Get("test-id")
	if updatedTask.Status != Completed {
		t.Errorf("Expected status 'Completed', got '%s'", updatedTask.Status)
	}
	if updatedTask.Progress != 100 {
		t.Errorf("Expected progress 100, got %d", updatedTask.Progress)
	}

	// Test updating non-existent task
	err = s.Update("non-existent", func(t *Task) {
		t.Status = Completed
	})
	if err != nil {
		t.Error("Should not return error for non-existent task update")
	}
}

func TestTaskTimestamps(t *testing.T) {
	s := NewMemoryStore()

	task := &Task{
		ID:       "timestamp-test",
		Status:   Pending,
		Progress: 0,
	}
	beforeAdd := time.Now()
	s.Save(task)
	afterAdd := time.Now()

	retrievedTask, _ := s.Get("timestamp-test")

	if retrievedTask.CreatedAt.Before(beforeAdd) || retrievedTask.CreatedAt.After(afterAdd) {
		t.Error("CreatedAt timestamp is not accurate")
	}

	if retrievedTask.UpdatedAt.Before(beforeAdd) || retrievedTask.UpdatedAt.After(afterAdd) {
		t.Error("UpdatedAt timestamp is not accurate after creation")
	}

	// Test UpdatedAt after update
	beforeUpdate := time.Now()
	s.Update("timestamp-test", func(t *Task) {
		t.Progress = 50
	})
	afterUpdate := time.Now()

	updatedTask, _ := s.Get("timestamp-test")
	if updatedTask.UpdatedAt.Before(beforeUpdate) || updatedTask.UpdatedAt.After(afterUpdate) {
		t.Error("UpdatedAt timestamp is not accurate after update")
	}
}