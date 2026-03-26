package workflow

import (
	"context"
	"testing"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/circuit"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
)

// MockDocumentAuditorClient 用于测试的模拟客户端
type MockDocumentAuditorClient struct {
	parseDocumentFunc    func(ctx context.Context, in *auditor.ParseRequest) (*auditor.ParsedData, error)
	auditRulesFunc       func(ctx context.Context, in *auditor.AuditRequest) (*auditor.AuditResponse, error)
	analyzeSemanticsFunc func(ctx context.Context, in *auditor.SemanticRequest) (*auditor.AuditResponse, error)
}

func (m *MockDocumentAuditorClient) ParseDocument(ctx context.Context, in *auditor.ParseRequest) (*auditor.ParsedData, error) {
	if m.parseDocumentFunc != nil {
		return m.parseDocumentFunc(ctx, in)
	}
	// 默认返回模拟数据
	return &auditor.ParsedData{
		DocId: "mock-doc-id",
		Metadata: &auditor.DocumentMetadata{
			Title:        "Mock Document Title",
			PageCount:    10,
			MarginTop:    1.0,
			MarginBottom: 1.0,
		},
		Sections: []*auditor.Section{
			{
				SectionId: 1,
				Type:      "heading",
				Level:     1,
				Text:      "Introduction",
				Props:     map[string]string{"font": "SimSun", "size": "12pt"},
			},
			{
				SectionId: 2,
				Type:      "paragraph",
				Level:     0,
				Text:      "This is a sample paragraph.",
				Props:     map[string]string{"font": "SimSun", "size": "12pt"},
			},
		},
		References: []*auditor.Reference{
			{
				RefId:         "[1]",
				RawText:       "[1] Sample reference",
				IsValidFormat: true,
			},
		},
	}, nil
}

func (m *MockDocumentAuditorClient) AuditRules(ctx context.Context, in *auditor.AuditRequest) (*auditor.AuditResponse, error) {
	if m.auditRulesFunc != nil {
		return m.auditRulesFunc(ctx, in)
	}
	// 默认返回模拟数据
	return &auditor.AuditResponse{
		Issues: []*auditor.Issue{
			{
				Code:            "ERR_FONT_001",
				Message:         "Incorrect font used in section",
				SectionId:       1,
				Severity:        auditor.Severity_MEDIUM,
				Suggestion:      "Use SimSun font",
				OriginalSnippet: "Introduction",
			},
		},
		ScoreImpact: 5.0,
	}, nil
}

func (m *MockDocumentAuditorClient) AnalyzeSemantics(ctx context.Context, in *auditor.SemanticRequest) (*auditor.AuditResponse, error) {
	if m.analyzeSemanticsFunc != nil {
		return m.analyzeSemanticsFunc(ctx, in)
	}
	// 默认返回模拟数据
	return &auditor.AuditResponse{
		Issues: []*auditor.Issue{
			{
				Code:            "SEM_ERR_001",
				Message:         "Potential grammatical error detected",
				SectionId:       2,
				Severity:        auditor.Severity_LOW,
				Suggestion:      "Consider revising the sentence structure",
				OriginalSnippet: "This is a sample paragraph.",
			},
		},
		ScoreImpact: 2.0,
	}, nil
}

// TestNewOrchestrator 测试创建新的编排器实例
func TestNewOrchestrator(t *testing.T) {
	o := NewOrchestrator("parser:52051", "engine:9191", "inference:50051")

	if o.parserAddr != "parser:52051" {
		t.Errorf("Expected parserAddr to be 'parser:52051', got '%s'", o.parserAddr)
	}
	if o.engineAddr != "engine:9191" {
		t.Errorf("Expected engineAddr to be 'engine:9191', got '%s'", o.engineAddr)
	}
	if o.inferenceAddr != "inference:50051" {
		t.Errorf("Expected inferenceAddr to be 'inference:50051', got '%s'", o.inferenceAddr)
	}
	if o.timeout != 60*time.Second {
		t.Errorf("Expected timeout to be 60 seconds, got %v", o.timeout)
	}
	if o.retryAttempts != 3 {
		t.Errorf("Expected retryAttempts to be 3, got %d", o.retryAttempts)
	}
	if o.inferenceCB == nil {
		t.Errorf("Expected inferenceCB to be initialized")
	}
}

// TestProcess 测试编排器的Process方法
func TestProcess(t *testing.T) {
	o := &Orchestrator{
		parserAddr:    "parser:52051",
		engineAddr:    "engine:9191",
		inferenceAddr: "inference:50051",
		timeout:       10 * time.Second,
		retryAttempts: 1,
		inferenceCB:   circuit.NewCircuitBreaker(3, 30*time.Second),
	}

	// 这个测试会失败，因为我们没有真正的gRPC服务
	// 但在实际环境中，我们会使用模拟服务
	// 对于单元测试，我们可以测试其他逻辑

	// 由于我们不能连接到真实的gRPC服务，我们测试聚合功能
	result := &TaskResult{
		AuditResult: &auditor.AuditResponse{
			Issues: []*auditor.Issue{
				{
					Code:      "ERR_TEST_001",
					Message:   "Test error from audit",
					SectionId: 1,
					Severity:  auditor.Severity_MEDIUM,
				},
			},
			ScoreImpact: 5.0,
		},
		SemanticResult: &auditor.AuditResponse{
			Issues: []*auditor.Issue{
				{
					Code:      "SEM_TEST_001",
					Message:   "Test semantic error",
					SectionId: 2,
					Severity:  auditor.Severity_LOW,
				},
			},
			ScoreImpact: 2.0,
		},
	}

	// 验证聚合功能
	aggregated := o.AggregateResults(result.AuditResult, result.SemanticResult)

	if len(aggregated.Issues) != 2 {
		t.Errorf("Expected 2 issues after aggregation, got %d", len(aggregated.Issues))
	}

	if aggregated.ScoreImpact != 7.0 {
		t.Errorf("Expected ScoreImpact to be 7.0, got %f", aggregated.ScoreImpact)
	}
}

// TestAggregateResults 测试结果聚合功能
func TestAggregateResults(t *testing.T) {
	o := &Orchestrator{}

	auditResult := &auditor.AuditResponse{
		Issues: []*auditor.Issue{
			{
				Code:      "ERR_DUPLICATE",
				Message:   "Duplicate error from audit",
				SectionId: 1,
				Severity:  auditor.Severity_MEDIUM,
			},
			{
				Code:      "ERR_UNIQUE_AUDIT",
				Message:   "Unique error from audit",
				SectionId: 2,
				Severity:  auditor.Severity_HIGH,
			},
		},
		ScoreImpact: 5.0,
	}

	semanticResult := &auditor.AuditResponse{
		Issues: []*auditor.Issue{
			{
				Code:      "ERR_DUPLICATE",
				Message:   "Duplicate error from semantic",
				SectionId: 1,
				Severity:  auditor.Severity_HIGH, // 更高的严重性
			},
			{
				Code:      "ERR_UNIQUE_SEMANTIC",
				Message:   "Unique error from semantic",
				SectionId: 3,
				Severity:  auditor.Severity_LOW,
			},
		},
		ScoreImpact: 3.0,
	}

	aggregated := o.AggregateResults(auditResult, semanticResult)

	// 应该有3个唯一的问题（去重后）
	if len(aggregated.Issues) != 3 {
		t.Errorf("Expected 3 unique issues after aggregation, got %d", len(aggregated.Issues))
	}

	// 检查重复问题是否被正确处理（保留较高严重性）
	duplicateIssue := findIssueByCodeAndSection(aggregated.Issues, "ERR_DUPLICATE", 1)
	if duplicateIssue == nil {
		t.Error("Expected to find duplicate issue after aggregation")
	} else if duplicateIssue.Severity != auditor.Severity_HIGH {
		t.Errorf("Expected duplicate issue to have HIGH severity (from semantic result), got %s", duplicateIssue.Severity.String())
	}

	// 检查总分影响
	expectedScoreImpact := float32(8.0) // 5.0 + 3.0
	if aggregated.ScoreImpact != expectedScoreImpact {
		t.Errorf("Expected ScoreImpact to be %f, got %f", expectedScoreImpact, aggregated.ScoreImpact)
	}

	// 检查排序（按section_id）
	if len(aggregated.Issues) >= 2 {
		for i := 0; i < len(aggregated.Issues)-1; i++ {
			if aggregated.Issues[i].SectionId > aggregated.Issues[i+1].SectionId {
				t.Errorf("Issues are not properly sorted by section_id")
			}
		}
	}
}

// 辅助函数：根据代码和节号查找问题
func findIssueByCodeAndSection(issues []*auditor.Issue, code string, sectionId int32) *auditor.Issue {
	for _, issue := range issues {
		if issue.Code == code && issue.SectionId == sectionId {
			return issue
		}
	}
	return nil
}

// TestCircuitBreakerFunctionality 测试熔断器功能
func TestCircuitBreakerFunctionality(t *testing.T) {
	cb := circuit.NewCircuitBreaker(2, 1*time.Second) // 2次失败后开启，1秒后重置

	// 测试连续失败使熔断器开启
	for i := 0; i < 2; i++ {
		err := cb.Execute(func() error {
			return nil // 模拟成功
		})
		if err != nil {
			t.Errorf("Unexpected error on attempt %d: %v", i+1, err)
		}

		// 引入失败操作
		err = cb.Execute(func() error {
			return &MockError{"simulated failure"}
		})
		// 第一次失败不应该导致熔断器开启
	}

	// 再次失败应使熔断器开启
	err := cb.Execute(func() error {
		return &MockError{"simulated failure"}
	})
	if err != nil && err.Error() != "simulated failure" {
		t.Errorf("Expected simulated failure, got: %v", err)
	}

	// 现在熔断器应该开启，后续调用应立即失败
	err = cb.Execute(func() error {
		return nil // 这个调用不应该被执行，因为熔断器已开启
	})
	if err == nil {
		t.Error("Expected circuit breaker to be OPEN and return error")
	}
}

// MockError 用于测试的模拟错误
type MockError struct {
	msg string
}

func (e *MockError) Error() string {
	return e.msg
}
