package store

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// RedisStore Redis存储实现
type RedisStore struct {
	client *redis.Client
	ttl    time.Duration
}

// NewRedisStore 创建新的Redis存储实例
func NewRedisStore(client *redis.Client, ttl time.Duration) *RedisStore {
	if ttl <= 0 {
		ttl = 24 * time.Hour // 默认24小时
	}
	return &RedisStore{
		client: client,
		ttl:    ttl,
	}
}

// Save 保存任务到Redis
func (r *RedisStore) Save(task *Task) error {
	data, err := json.Marshal(task)
	if err != nil {
		return fmt.Errorf("failed to marshal task: %w", err)
	}

	ctx := context.Background()
	err = r.client.SetEx(ctx, "task:"+task.ID, data, r.ttl).Err()
	if err != nil {
		return fmt.Errorf("failed to save task to Redis: %w", err)
	}

	return nil
}

// Get 从Redis获取任务
func (r *RedisStore) Get(taskID string) (*Task, bool) {
	ctx := context.Background()
	data, err := r.client.Get(ctx, "task:"+taskID).Result()
	if err != nil {
		if err == redis.Nil {
			return nil, false // 键不存在
		}
		// 记录错误但不中断程序
		fmt.Printf("Redis get error: %v\n", err)
		return nil, false
	}

	var task Task
	err = json.Unmarshal([]byte(data), &task)
	if err != nil {
		fmt.Printf("Failed to unmarshal task: %v\n", err)
		return nil, false
	}

	return &task, true
}

// Update 更新任务
func (r *RedisStore) Update(taskID string, updater func(*Task)) error {
	task, exists := r.Get(taskID)
	if !exists {
		return fmt.Errorf("task not found: %s", taskID)
	}

	updater(task)
	return r.Save(task)
}

// Delete 删除任务
func (r *RedisStore) Delete(taskID string) error {
	ctx := context.Background()
	err := r.client.Del(ctx, "task:"+taskID).Err()
	if err != nil {
		return fmt.Errorf("failed to delete task from Redis: %w", err)
	}
	return nil
}