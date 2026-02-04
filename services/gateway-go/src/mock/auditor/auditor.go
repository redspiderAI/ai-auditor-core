package auditor

import (
	"context"
	"google.golang.org/grpc"
)

// Mock protobuf-generated types for the academic auditor service based on auditor.proto

// DocumentAuditorClient interface
type DocumentAuditorClient interface {
	ParseDocument(ctx context.Context, in *ParseRequest, opts ...grpc.CallOption) (*ParsedData, error)
	AuditRules(ctx context.Context, in *AuditRequest, opts ...grpc.CallOption) (*AuditResponse, error)
	AnalyzeSemantics(ctx context.Context, in *SemanticRequest, opts ...grpc.CallOption) (*AuditResponse, error)
}

// Mock implementation
type MockDocumentAuditorClient struct{}

func (m *MockDocumentAuditorClient) ParseDocument(ctx context.Context, in *ParseRequest, opts ...grpc.CallOption) (*ParsedData, error) {
	// Mock implementation returning sample data
	return &ParsedData{
		DocId: "mock-doc-id",
		Metadata: &DocumentMetadata{
			Title:       "Mock Document Title",
			PageCount:   10,
			MarginTop:   1.0,
			MarginBottom: 1.0,
		},
		Sections: []*Section{
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
		References: []*Reference{
			{
				RefId:        "[1]",
				RawText:      "[1] Sample reference",
				IsValidFormat: true,
			},
		},
	}, nil
}

func (m *MockDocumentAuditorClient) AuditRules(ctx context.Context, in *AuditRequest, opts ...grpc.CallOption) (*AuditResponse, error) {
	// Mock implementation returning sample issues
	return &AuditResponse{
		Issues: []*Issue{
			{
				Code:            "ERR_FONT_001",
				Message:         "Incorrect font used in section",
				SectionId:       1,
				Severity:        Severity_MEDIUM,
				Suggestion:      "Use SimSun font",
				OriginalSnippet: "Introduction",
			},
		},
		ScoreImpact: 5.0,
	}, nil
}

func (m *MockDocumentAuditorClient) AnalyzeSemantics(ctx context.Context, in *SemanticRequest, opts ...grpc.CallOption) (*AuditResponse, error) {
	// Mock implementation returning sample semantic issues
	return &AuditResponse{
		Issues: []*Issue{
			{
				Code:            "SEM_ERR_001",
				Message:         "Potential grammatical error detected",
				SectionId:       2,
				Severity:        Severity_LOW,
				Suggestion:      "Consider revising the sentence structure",
				OriginalSnippet: "This is a sample paragraph.",
			},
		},
		ScoreImpact: 2.0,
	}, nil
}

// Helper function to create a new client
func NewDocumentAuditorClient(cc *grpc.ClientConn) DocumentAuditorClient {
	return &MockDocumentAuditorClient{}
}

// Message definitions based on auditor.proto
type ParseRequest struct {
	FilePath    string `protobuf:"bytes,1,opt,name=file_path,json=filePath,proto3" json:"file_path,omitempty"`
	TemplateType string `protobuf:"bytes,2,opt,name=template_type,json=templateType,proto3" json:"template_type,omitempty"`
}

type ParsedData struct {
	DocId      string             `protobuf:"bytes,1,opt,name=doc_id,json=docId,proto3" json:"doc_id,omitempty"`
	Metadata   *DocumentMetadata  `protobuf:"bytes,2,opt,name=metadata,proto3" json:"metadata,omitempty"`
	Sections   []*Section         `protobuf:"bytes,3,rep,name=sections,proto3" json:"sections,omitempty"`
	References []*Reference       `protobuf:"bytes,4,rep,name=references,proto3" json:"references,omitempty"`
}

type DocumentMetadata struct {
	Title         string  `protobuf:"bytes,1,opt,name=title,proto3" json:"title,omitempty"`
	PageCount     int32   `protobuf:"varint,2,opt,name=page_count,json=pageCount,proto3" json:"page_count,omitempty"`
	MarginTop     float32 `protobuf:"fixed32,3,opt,name=margin_top,json=marginTop,proto3" json:"margin_top,omitempty"`
	MarginBottom  float32 `protobuf:"fixed32,4,opt,name=margin_bottom,json=marginBottom,proto3" json:"margin_bottom,omitempty"`
}

type Section struct {
	SectionId int32             `protobuf:"varint,1,opt,name=section_id,json=sectionId,proto3" json:"section_id,omitempty"`
	Type      string            `protobuf:"bytes,2,opt,name=type,proto3" json:"type,omitempty"`
	Level     int32             `protobuf:"varint,3,opt,name=level,proto3" json:"level,omitempty"`
	Text      string            `protobuf:"bytes,4,opt,name=text,proto3" json:"text,omitempty"`
	Props     map[string]string `protobuf:"bytes,5,rep,name=props,proto3" json:"props,omitempty" protobuf_key:"bytes,1,opt,name=key,proto3" protobuf_val:"bytes,2,opt,name=value,proto3"`
}

type Reference struct {
	RefId        string `protobuf:"bytes,1,opt,name=ref_id,json=refId,proto3" json:"ref_id,omitempty"`
	RawText      string `protobuf:"bytes,2,opt,name=raw_text,json=rawText,proto3" json:"raw_text,omitempty"`
	IsValidFormat bool  `protobuf:"varint,3,opt,name=is_valid_format,json=isValidFormat,proto3" json:"is_valid_format,omitempty"`
}

type AuditRequest struct {
	Data        *ParsedData `protobuf:"bytes,1,opt,name=data,proto3" json:"data,omitempty"`
	TargetRuleSet string    `protobuf:"bytes,2,opt,name=target_rule_set,json=targetRuleSet,proto3" json:"target_rule_set,omitempty"`
}

type SemanticRequest struct {
	Sections    []*Section `protobuf:"bytes,1,rep,name=sections,proto3" json:"sections,omitempty"`
	ModelVersion string    `protobuf:"bytes,2,opt,name=model_version,json=modelVersion,proto3" json:"model_version,omitempty"`
}

type AuditResponse struct {
	Issues      []*Issue `protobuf:"bytes,1,rep,name=issues,proto3" json:"issues,omitempty"`
	ScoreImpact float32  `protobuf:"fixed32,2,opt,name=score_impact,json=scoreImpact,proto3" json:"score_impact,omitempty"`
}

type Issue struct {
	Code            string     `protobuf:"bytes,1,opt,name=code,proto3" json:"code,omitempty"`
	Message         string     `protobuf:"bytes,2,opt,name=message,proto3" json:"message,omitempty"`
	SectionId       int32      `protobuf:"varint,3,opt,name=section_id,json=sectionId,proto3" json:"section_id,omitempty"`
	Severity        Severity   `protobuf:"varint,4,opt,name=severity,proto3,enum=academic.auditor.Severity" json:"severity,omitempty"`
	Suggestion      string     `protobuf:"bytes,5,opt,name=suggestion,proto3" json:"suggestion,omitempty"`
	OriginalSnippet string     `protobuf:"bytes,6,opt,name=original_snippet,json=originalSnippet,proto3" json:"original_snippet,omitempty"`
}

type Severity int32

const (
	Severity_INFO      Severity = 0
	Severity_LOW       Severity = 1
	Severity_MEDIUM    Severity = 2
	Severity_HIGH      Severity = 3
	Severity_CRITICAL  Severity = 4
)

func (x Severity) String() string {
	switch x {
	case Severity_INFO:
		return "INFO"
	case Severity_LOW:
		return "LOW"
	case Severity_MEDIUM:
		return "MEDIUM"
	case Severity_HIGH:
		return "HIGH"
	case Severity_CRITICAL:
		return "CRITICAL"
	default:
		return "UNKNOWN"
	}
}