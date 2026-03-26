package workflow

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/circuit"
	auditorpb "github.com/redspiderAI/ai-auditor-core/shared/protos"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/utils"
	"google.golang.org/grpc"
)

// TaskResult 存储任务执行结果
type TaskResult struct {
	ParseResult    *auditorpb.ParsedData
	AuditResult    *auditorpb.AuditResponse
	SemanticResult *auditorpb.AuditResponse
	AnnotatedPath  string
	Error          error
}

// Orchestrator 分布式审查编排器
type Orchestrator struct {
	parserAddr    string
	engineAddr    string
	inferenceAddr string
	timeout       time.Duration
	retryAttempts int
	inferenceCB   *circuit.CircuitBreaker // Python推理服务的熔断器
}

// NewOrchestrator 创建新的编排器实例
func NewOrchestrator(parserAddr, engineAddr, inferenceAddr string) *Orchestrator {
	return &Orchestrator{
		parserAddr:    parserAddr,
		engineAddr:    engineAddr,
		inferenceAddr: inferenceAddr,
		timeout:       60 * time.Second,
		retryAttempts: 3,
		inferenceCB:   circuit.NewCircuitBreaker(3, 30*time.Second), // 最大3次失败，30秒后重置
	}
}

// Process 审查流程编排：先调用A(Rust)，然后并行调用B(Java)和C(Python)
func (o *Orchestrator) Process(ctx context.Context, filePath string) (*TaskResult, error) {
	// 检查是否启用应急模式
	if os.Getenv("EMERGENCY_MODE") == "true" {
		log.Println("⚠️ 启动紧急模式：跳过 Rust/Java，直连 Python")
		return o.emergencyProcess(ctx, filePath)
	}

	result := &TaskResult{}

	// 步骤1: 调用成员A (Rust解析器) 获取ParsedData
	parsedData, err := o.callParser(ctx, filePath)
	if err != nil {
		return nil, fmt.Errorf("解析文档失败: %w", err)
	}
	result.ParseResult = parsedData

	// 步骤2: 并行调用成员B (Java引擎) 和成员C (Python推理)
	wg := sync.WaitGroup{}
	errChan := make(chan error, 2)

	wg.Add(2)

	// 并行调用Java引擎
	go func() {
		defer wg.Done()
		auditResult, err := o.callEngine(ctx, parsedData)
		if err != nil {
			errChan <- fmt.Errorf("规则审计失败: %w", err)
			return
		}
		result.AuditResult = auditResult
	}()

	// 并行调用Python推理
	go func() {
		defer wg.Done()
		semanticResult, err := o.callInference(ctx, parsedData)
		if err != nil {
			errChan <- fmt.Errorf("语义分析失败: %w", err)
			return
		}
		result.SemanticResult = semanticResult
	}()

	wg.Wait()
	close(errChan)

	// 检查是否有错误发生
	if err := <-errChan; err != nil {
		return nil, err
	}

	return result, nil
}

// emergencyProcess 应急处理流程：直接将文件发送到Python进行快速审计
func (o *Orchestrator) emergencyProcess(ctx context.Context, filePath string) (*TaskResult, error) {
	// 读取文件内容
	fileData, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	// 调用应急通道
	respBody, err := utils.EmergencyFastTrack(ctx, "", filepath.Base(filePath), fileData)
	if err != nil {
		// 不返回错误，而是创建一个包含错误信息的任务结果
		// 这样可以让worker继续处理其他任务，而不是退出
		log.Printf("⚠️ 应急模式调用Python服务失败: %v", err)
		
		// 构造一个错误状态的TaskResult
		taskResult := &TaskResult{
			ParseResult:    nil, // 应急模式下无解析结果
			AuditResult:    nil, // 应急模式下无规则审计结果
			SemanticResult: nil, // 应急模式下暂时为空，后续可扩展
			Error:          fmt.Errorf("emergency audit failed: %w", err),
		}
		
		return taskResult, nil
	}

	// 解析返回结果
	var result map[string]interface{}
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	// 构造 TaskResult
	taskResult := &TaskResult{
		ParseResult:    nil, // 应急模式下无解析结果
		AuditResult:    nil, // 应急模式下无规则审计结果
		SemanticResult: nil, // 应急模式下暂时为空，后续可扩展
		Error:          nil,
	}

	// 记录应急模式处理完成
	log.Println("✅ 应急模式处理完成")

	return taskResult, nil
}

// 通用重试函数
func (o *Orchestrator) withRetry(operation func() error) error {
	var err error
	for attempt := 0; attempt < o.retryAttempts; attempt++ {
		err = operation()
		if err == nil {
			return nil
		}

		log.Printf("操作失败 (尝试 %d/%d): %v", attempt+1, o.retryAttempts, err)

		if attempt < o.retryAttempts-1 {
			// 等待一段时间后重试（指数退避）
			time.Sleep(time.Duration(attempt+1) * time.Second)
		}
	}
	return err
}

// 通用gRPC客户端创建函数
func (o *Orchestrator) createGRPCClient(ctx context.Context, addr string) (*grpc.ClientConn, error) {
	ctx, cancel := context.WithTimeout(ctx, o.timeout)
	defer cancel()

	return grpc.DialContext(ctx, addr, grpc.WithInsecure(), grpc.WithBlock())
}

// callParser 调用Rust解析服务
func (o *Orchestrator) callParser(ctx context.Context, filePath string) (*auditorpb.ParsedData, error) {
	var parsedData *auditorpb.ParsedData
	var err error

	// 重试机制
	err = o.withRetry(func() error {
		parsedData, err = o.tryCallParser(ctx, filePath)
		return err
	})

	return parsedData, err
}

// tryCallParser 尝试调用解析器
func (o *Orchestrator) tryCallParser(ctx context.Context, filePath string) (*auditorpb.ParsedData, error) {
	conn, err := o.createGRPCClient(ctx, o.parserAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到解析器: %w", err)
	}
	defer conn.Close()

	client := auditorpb.NewDocumentAuditorClient(conn)
	req := &auditorpb.ParseRequest{
		FilePath:     filePath,
		TemplateType: "GB/T7714", // 默认模板类型
	}

	return client.ParseDocument(ctx, req)
}

// callEngine 调用Java引擎服务
func (o *Orchestrator) callEngine(ctx context.Context, parsedData *auditorpb.ParsedData) (*auditorpb.AuditResponse, error) {
	var auditResult *auditorpb.AuditResponse
	var err error

	// 重试机制
	err = o.withRetry(func() error {
		auditResult, err = o.tryCallEngine(ctx, parsedData)
		return err
	})

	return auditResult, err
}

// tryCallEngine 尝试调用引擎
func (o *Orchestrator) tryCallEngine(ctx context.Context, parsedData *auditorpb.ParsedData) (*auditorpb.AuditResponse, error) {
	conn, err := o.createGRPCClient(ctx, o.engineAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到引擎: %w", err)
	}
	defer conn.Close()

	client := auditorpb.NewDocumentAuditorClient(conn)
	req := &auditorpb.AuditRequest{
		Data:          parsedData,
		TargetRuleSet: "academic-standard-v1", // 默认规则集
	}

	return client.AuditRules(ctx, req)
}

// callInference 调用Python推理服务
func (o *Orchestrator) callInference(ctx context.Context, parsedData *auditorpb.ParsedData) (*auditorpb.AuditResponse, error) {
	var semanticResult *auditorpb.AuditResponse
	var err error

	// 使用熔断器包装对Python服务的调用
	err = o.inferenceCB.Execute(func() error {
		callErr := o.withRetry(func() error {
			var tempResult *auditorpb.AuditResponse
			var tempErr error

			tempResult, tempErr = o.tryCallInference(ctx, parsedData)
			if tempErr == nil {
				semanticResult = tempResult
				// 成功时重置熔断器
				o.inferenceCB.Reset()
			}
			return tempErr
		})

		return callErr
	})

	return semanticResult, err
}

// tryCallInference 尝调用推理服务
func (o *Orchestrator) tryCallInference(ctx context.Context, parsedData *auditorpb.ParsedData) (*auditorpb.AuditResponse, error) {
	conn, err := o.createGRPCClient(ctx, o.inferenceAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到推理服务: %w", err)
	}
	defer conn.Close()

	client := auditorpb.NewDocumentAuditorClient(conn)
	req := &auditorpb.SemanticRequest{
		Sections:     parsedData.Sections,
		ModelVersion: "qwen-2.5-72b", // 默认模型版本
	}

	return client.AnalyzeSemantics(ctx, req)
}

// AggregateResults 聚合来自B和C的结果，剔除重复项并按section_id排序
func (o *Orchestrator) AggregateResults(auditResult *auditorpb.AuditResponse, semanticResult *auditorpb.AuditResponse) *auditorpb.AuditResponse {
	// 创建一个map来去重，key为issue的code+section_id组合
	uniqueIssues := make(map[string]*auditorpb.Issue)

	// 处理规则审计结果
	for _, issue := range auditResult.Issues {
		key := fmt.Sprintf("%s_%d", issue.Code, issue.SectionId)
		uniqueIssues[key] = issue
	}

	// 处理语义分析结果
	for _, issue := range semanticResult.Issues {
		key := fmt.Sprintf("%s_%d", issue.Code, issue.SectionId)
		// 如果相同key的issue不存在，则添加；如果存在且严重程度更高，则替换
		existingIssue, exists := uniqueIssues[key]
		if !exists || issue.Severity > existingIssue.Severity {
			uniqueIssues[key] = issue
		}
	}

	// 将map转换为切片
	aggregatedIssues := make([]*auditorpb.Issue, 0, len(uniqueIssues))
	for _, issue := range uniqueIssues {
		aggregatedIssues = append(aggregatedIssues, issue)
	}

	// 按section_id排序
	for i := 0; i < len(aggregatedIssues)-1; i++ {
		for j := i + 1; j < len(aggregatedIssues); j++ {
			if aggregatedIssues[i].SectionId > aggregatedIssues[j].SectionId {
				aggregatedIssues[i], aggregatedIssues[j] = aggregatedIssues[j], aggregatedIssues[i]
			}
		}
	}

	// 计算综合影响分数
	totalScoreImpact := auditResult.ScoreImpact + semanticResult.ScoreImpact

	return &auditorpb.AuditResponse{
		Issues:      aggregatedIssues,
		ScoreImpact: totalScoreImpact,
	}
}

// AnnotateDocument 调用成员A回写接口生成带批注的文档
func (o *Orchestrator) AnnotateDocument(ctx context.Context, filePath string, issues []*auditorpb.Issue) (string, error) {
	var annotatedPath string
	var err error

	err = o.withRetry(func() error {
		annotatedPath, err = o.tryAnnotateDocument(ctx, filePath, issues)
		return err
	})

	return annotatedPath, err
}

func (o *Orchestrator) tryAnnotateDocument(ctx context.Context, filePath string, issues []*auditorpb.Issue) (string, error) {
	conn, err := o.createGRPCClient(ctx, o.parserAddr)
	if err != nil {
		return "", fmt.Errorf("无法连接到解析器进行回写: %w", err)
	}
	defer conn.Close()

	client := auditorpb.NewDocumentAuditorClient(conn)
	resp, err := client.InjectAnnotations(ctx, &auditorpb.InjectRequest{FilePath: filePath, Issues: issues})
	if err != nil {
		return "", err
	}
	return resp.AnnotatedPath, nil
}
