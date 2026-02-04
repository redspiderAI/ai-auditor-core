package grpcclient

import (
	"context"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/mock/auditor"
)

// DocumentAuditorClient 封装真实的gRPC客户端
type DocumentAuditorClient struct {
	conn   *grpc.ClientConn
	client auditor.DocumentAuditorClient
}

// NewClient 创建新的gRPC客户端
func NewClient(address string, timeout time.Duration) (*DocumentAuditorClient, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	conn, err := grpc.DialContext(
		ctx,
		address,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
	)
	if err != nil {
		return nil, err
	}

	return &DocumentAuditorClient{
		conn:   conn,
		client: auditor.NewDocumentAuditorClient(conn),
	}, nil
}

// Close 关闭连接
func (c *DocumentAuditorClient) Close() error {
	return c.conn.Close()
}

// ParseDocument 调用解析服务
func (c *DocumentAuditorClient) ParseDocument(ctx context.Context, req *auditor.ParseRequest) (*auditor.ParsedData, error) {
	return c.client.ParseDocument(ctx, req)
}

// AuditRules 调用规则引擎
func (c *DocumentAuditorClient) AuditRules(ctx context.Context, req *auditor.AuditRequest) (*auditor.AuditResponse, error) {
	return c.client.AuditRules(ctx, req)
}

// AnalyzeSemantics 调用语义分析
func (c *DocumentAuditorClient) AnalyzeSemantics(ctx context.Context, req *auditor.SemanticRequest) (*auditor.AuditResponse, error) {
	return c.client.AnalyzeSemantics(ctx, req)
}