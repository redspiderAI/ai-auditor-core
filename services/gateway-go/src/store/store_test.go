package store

import "testing"

func TestStoreAddGetUpdate(t *testing.T) {
	s := NewStore()
	task := &Task{ID: "t1", Status: "Pending", Progress: 0}
	s.AddTask(task)

	if got, ok := s.GetTask("t1"); !ok {
		t.Fatalf("task not found")
	} else if got.Status != "Pending" {
		t.Fatalf("unexpected status: %s", got.Status)
	}

	updated := s.UpdateTask("t1", func(t *Task) {
		t.Status = "Running"
		t.Progress = 50
	})
	if !updated {
		t.Fatalf("expected update to succeed")
	}

	if got, ok := s.GetTask("t1"); !ok || got.Status != "Running" || got.Progress != 50 {
		t.Fatalf("task not updated, got: %+v", got)
	}
}
