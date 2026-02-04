package circuit

import (
	"errors"
	"sync"
	"time"
)

// State 表示熔断器的状态
type State int

const (
	StateClosed State = iota // 关闭状态 - 正常调用
	StateOpen              // 开启状态 - 拒绝调用
	StateHalfOpen          // 半开状态 - 尝试恢复
)

// CircuitBreaker 熔断器结构
type CircuitBreaker struct {
	state          State
	mutex          sync.Mutex
	failureCount   int
	lastFailure    time.Time
	maxFailures    int
	resetTimeout   time.Duration
	halfOpenTime   time.Time
	halfOpenTimeout time.Duration
}

// NewCircuitBreaker 创建新的熔断器
func NewCircuitBreaker(maxFailures int, resetTimeout time.Duration) *CircuitBreaker {
	return &CircuitBreaker{
		state:         StateClosed,
		maxFailures:   maxFailures,
		resetTimeout:  resetTimeout,
		halfOpenTimeout: 1 * time.Second, // 半开状态的超时时间
	}
}

// Execute 执行受保护的操作
func (cb *CircuitBreaker) Execute(operation func() error) error {
	cb.mutex.Lock()
	
	// 检查是否需要从开启状态切换到半开状态
	if cb.state == StateOpen && time.Since(cb.lastFailure) >= cb.resetTimeout {
		cb.state = StateHalfOpen
		cb.halfOpenTime = time.Now()
	}
	
	currentState := cb.state
	cb.mutex.Unlock()
	
	switch currentState {
	case StateClosed:
		return cb.callProtectedOperation(operation)
	case StateOpen:
		return errors.New("circuit breaker is OPEN")
	case StateHalfOpen:
		return cb.attemptReset(operation)
	default:
		return errors.New("unknown circuit breaker state")
	}
}

// callProtectedOperation 调用受保护的操作
func (cb *CircuitBreaker) callProtectedOperation(operation func() error) error {
	err := operation()
	
	cb.mutex.Lock()
	defer cb.mutex.Unlock()
	
	if err != nil {
		// 操作失败，增加失败计数
		cb.failureCount++
		cb.lastFailure = time.Now()
		
		// 如果失败次数超过阈值，切换到开启状态
		if cb.failureCount >= cb.maxFailures {
			cb.state = StateOpen
		}
	} else {
		// 操作成功，重置失败计数
		cb.failureCount = 0
	}
	
	return err
}

// attemptReset 尝试重置熔断器
func (cb *CircuitBreaker) attemptReset(operation func() error) error {
	// 检查半开状态是否超时
	cb.mutex.Lock()
	if time.Since(cb.halfOpenTime) > cb.halfOpenTimeout {
		// 超时，回到开启状态
		cb.state = StateOpen
		cb.mutex.Unlock()
		return errors.New("circuit breaker is OPEN (half-open timeout)")
	}
	cb.mutex.Unlock()
	
	err := operation()
	
	cb.mutex.Lock()
	defer cb.mutex.Unlock()
	
	if err != nil {
		// 尝试失败，回到开启状态
		cb.state = StateOpen
		cb.failureCount = cb.maxFailures // 保持高失败计数
		cb.lastFailure = time.Now()
	} else {
		// 尝试成功，回到关闭状态
		cb.state = StateClosed
		cb.failureCount = 0
	}
	
	return err
}

// Reset 重置熔断器
func (cb *CircuitBreaker) Reset() {
	cb.mutex.Lock()
	defer cb.mutex.Unlock()
	
	cb.state = StateClosed
	cb.failureCount = 0
}

// GetState 获取当前状态
func (cb *CircuitBreaker) GetState() State {
	cb.mutex.Lock()
	defer cb.mutex.Unlock()
	
	return cb.state
}