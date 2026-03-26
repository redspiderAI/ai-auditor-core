package queue

import (
	"context"
)

// TaskQueue defines a minimal enqueue/dequeue contract.
type TaskQueue interface {
	Enqueue(ctx context.Context, id string) error
	Dequeue(ctx context.Context) (string, error)
	Close() error
}

// InMemoryQueue is a bounded channel-backed queue for fallback/local use.
type InMemoryQueue struct {
	ch chan string
}

// NewInMemoryQueue creates an in-memory queue with a given capacity.
func NewInMemoryQueue(size int) *InMemoryQueue {
	return &InMemoryQueue{ch: make(chan string, size)}
}

// Enqueue pushes a task ID into the queue or returns context cancellation.
func (q *InMemoryQueue) Enqueue(ctx context.Context, id string) error {
	select {
	case q.ch <- id:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// Dequeue pops a task ID or exits on context cancellation.
func (q *InMemoryQueue) Dequeue(ctx context.Context) (string, error) {
	select {
	case id := <-q.ch:
		return id, nil
	case <-ctx.Done():
		return "", ctx.Err()
	}
}

// Close closes the underlying channel.
func (q *InMemoryQueue) Close() error {
	close(q.ch)
	return nil
}

// Chan exposes the underlying channel for legacy consumers.
func (q *InMemoryQueue) Chan() <-chan string {
	return q.ch
}
