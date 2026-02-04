package workflow

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/circuit"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/grpcclient"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
	"google.golang.org/grpc"
)

// TaskResult 存储任务执行结果
type TaskResult struct {
	ParseResult   *auditor.ParsedData
	AuditResult   *auditor.AuditResponse
	SemanticResult *auditor.AuditResponse
	Error         error
}

// Orchestrator 分布式审查编排器
type Orchestrator struct {
	parserAddr      string
	engineAddr      string
	inferenceAddr   string
	timeout         time.Duration
	retryAttempts   int
	inferenceCB     *circuit.CircuitBreaker  // Python推理服务的熔断器
	parserClient    *grpcclient.DocumentAuditorClient
	engineClient    *grpcclient.DocumentAuditorClient
	inferenceClient *grpcclient.DocumentAuditorClient
}

// NewOrchestrator 创建新的编排器实例
func NewOrchestrator(parserAddr, engineAddr, inferenceAddr string) *Orchestrator {
	parserClient, _ := grpcclient.NewClient(parserAddr, 60*time.Second)
	engineClient, _ := grpcclient.NewClient(engineAddr, 60*time.Second)
	inferenceClient, _ := grpcclient.NewClient(inferenceAddr, 60*time.Second)

	return &Orchestrator{
		parserAddr:      parserAddr,
		engineAddr:      engineAddr,
		inferenceAddr:   inferenceAddr,
		timeout:         60 * time.Second,
		retryAttempts:   3,
		inferenceCB:     circuit.NewCircuitBreaker(3, 30*time.Second), // 最大3次失败，30秒后重置
		parserClient:    parserClient,
		engineClient:    engineClient,
		inferenceClient: inferenceClient,
	}
}

// Process 审查流程编排：先调用A(Rust)，然后并行调用B(Java)和C(Python)
func (o *Orchestrator) Process(ctx context.Context, filePath string) (*TaskResult, error) {
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
func (o *Orchestrator) callParser(ctx context.Context, filePath string) (*auditor.ParsedData, error) {
	var parsedData *auditor.ParsedData
	var err error

	// 重试机制
	err = o.withRetry(func() error {
		parsedData, err = o.tryCallParser(ctx, filePath)
		return err
	})

	return parsedData, err
}

// tryCallParser 尝试调用解析器
func (o *Orchestrator) tryCallParser(ctx context.Context, filePath string) (*auditor.ParsedData, error) {
	conn, err := o.createGRPCClient(ctx, o.parserAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到解析器: %w", err)
	}
	defer conn.Close()

	client := auditor.NewDocumentAuditorClient(conn)
	req := &auditor.ParseRequest{
		FilePath:    filePath,
		TemplateType: "GB/T7714", // 默认模板类型
	}

	return client.ParseDocument(ctx, req)
}

// callEngine 调用Java引擎服务
func (o *Orchestrator) callEngine(ctx context.Context, parsedData *auditor.ParsedData) (*auditor.AuditResponse, error) {
	var auditResult *auditor.AuditResponse
	var err error

	// 重试机制
	err = o.withRetry(func() error {
		auditResult, err = o.tryCallEngine(ctx, parsedData)
		return err
	})

	return auditResult, err
}

// tryCallEngine 尝试调用引擎
func (o *Orchestrator) tryCallEngine(ctx context.Context, parsedData *auditor.ParsedData) (*auditor.AuditResponse, error) {
	conn, err := o.createGRPCClient(ctx, o.engineAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到引擎: %w", err)
	}
	defer conn.Close()

	client := auditor.NewDocumentAuditorClient(conn)
	req := &auditor.AuditRequest{
		Data:        parsedData,
		TargetRuleSet: "academic-standard-v1", // 默认规则集
	}

	return client.AuditRules(ctx, req)
}

// callInference 调用Python推理服务
func (o *Orchestrator) callInference(ctx context.Context, parsedData *auditor.ParsedData) (*auditor.AuditResponse, error) {
	var semanticResult *auditor.AuditResponse
	var err error

	// 使用熔断器包装对Python服务的调用
	err = o.inferenceCB.Execute(func() error {
		callErr := o.withRetry(func() error {
			var tempResult *auditor.AuditResponse
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
func (o *Orchestrator) tryCallInference(ctx context.Context, parsedData *auditor.ParsedData) (*auditor.AuditResponse, error) {
	conn, err := o.createGRPCClient(ctx, o.inferenceAddr)
	if err != nil {
		return nil, fmt.Errorf("无法连接到推理服务: %w", err)
	}
	defer conn.Close()

	client := auditor.NewDocumentAuditorClient(conn)
	req := &auditor.SemanticRequest{
		Sections:     parsedData.Sections,
		ModelVersion: "qwen-2.5-72b", // 默认模型版本
	}

	return client.AnalyzeSemantics(ctx, req)
}

// AggregateResults 聚合来自B和C的结果，剔除重复项并按section_id排序
func (o *Orchestrator) AggregateResults(auditResult *auditor.AuditResponse, semanticResult *auditor.AuditResponse) *auditor.AuditResponse {
	// 创建一个map来去重，key为issue的code+section_id组合
	uniqueIssues := make(map[string]*auditor.Issue)
	
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
	aggregatedIssues := make([]*auditor.Issue, 0, len(uniqueIssues))
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
	
	return &auditor.AuditResponse{
		Issues:      aggregatedIssues,
		ScoreImpact: totalScoreImpact,
	}
}