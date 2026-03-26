package queue

import (
	"context"
	"errors"
	"time"

	"github.com/redis/go-redis/v9"
)

// RedisStreamQueue implements TaskQueue using Redis Streams + consumer group.
type RedisStreamQueue struct {
	client   *redis.Client
	stream   string
	group    string
	consumer string
	block    time.Duration
}

// NewRedisStreamQueue initializes client, stream, and consumer group.
func NewRedisStreamQueue(addr, stream, group, consumer string) (*RedisStreamQueue, error) {
	if addr == "" {
		return nil, errors.New("redis address is required")
	}
	if stream == "" {
		stream = "audit_tasks"
	}
	if group == "" {
		group = "gateway_group"
	}
	if consumer == "" {
		consumer = "gateway_consumer"
	}

	client := redis.NewClient(&redis.Options{Addr: addr})
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		return nil, err
	}

	// Ensure stream and group exist (MKSTREAM will create stream if missing).
	_ = client.XGroupCreateMkStream(ctx, stream, group, "$").Err()

	return &RedisStreamQueue{
		client:   client,
		stream:   stream,
		group:    group,
		consumer: consumer,
		block:    5 * time.Second,
	}, nil
}

// Enqueue adds a task ID to the Redis stream.
func (q *RedisStreamQueue) Enqueue(ctx context.Context, id string) error {
	return q.client.XAdd(ctx, &redis.XAddArgs{
		Stream: q.stream,
		Values: map[string]any{"task_id": id},
	}).Err()
}

// Dequeue reads one entry from the consumer group, ACKs it, and returns the task ID.
func (q *RedisStreamQueue) Dequeue(ctx context.Context) (string, error) {
	for {
		streams, err := q.client.XReadGroup(ctx, &redis.XReadGroupArgs{
			Group:    q.group,
			Consumer: q.consumer,
			Streams:  []string{q.stream, ">"},
			Count:    1,
			Block:    q.block,
		}).Result()

		if err != nil {
			if errors.Is(err, redis.Nil) {
				continue
			}
			return "", err
		}

		for _, st := range streams {
			for _, msg := range st.Messages {
				taskIDRaw, ok := msg.Values["task_id"]
				if !ok {
					_ = q.client.XAck(ctx, q.stream, q.group, msg.ID).Err()
					continue
				}
				taskID, _ := taskIDRaw.(string)
				_ = q.client.XAck(ctx, q.stream, q.group, msg.ID).Err()
				return taskID, nil
			}
		}
	}
}

// Close shuts down the Redis client.
func (q *RedisStreamQueue) Close() error {
	return q.client.Close()
}
