package store

// TaskStore 定义任务存储接口
type TaskStore interface {
    Save(task *Task) error
    Get(taskID string) (*Task, bool)
    Update(taskID string, updater func(*Task)) error
    Delete(taskID string) error
}