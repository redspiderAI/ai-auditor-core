"""测试脚本：验证gRPC服务功能
"""
import grpc
import sys
import os

# 添加项目路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from src.protos import auditor_pb2, auditor_pb2_grpc


def test_semantic_analysis():
    """测试语义分析功能"""
    with grpc.insecure_channel('127.0.0.1:50051') as channel:
        stub = auditor_pb2_grpc.DocumentAuditorStub(channel)
        
        # 创建测试请求
        section = auditor_pb2.Section()
        section.section_id = 1
        section.type = "test"
        section.text = "这个算法听说效果特别好，而且黏度测量也很准确，份量刚刚好。"
        
        request = auditor_pb2.SemanticRequest()
        request.sections.append(section)
        
        try:
            response = stub.AnalyzeSemantics(request)
            print(f"语义分析完成，发现问题数量: {len(response.issues)}")
            for issue in response.issues:
                print(f"  - 代码: {issue.code}, 消息: {issue.message}, 原文: {issue.original_snippet}, 建议: {issue.suggestion}")
            print(f"影响分数: {response.score_impact}")
        except Exception as e:
            print(f"语义分析测试失败: {e}")


def test_audit_rules():
    """测试规则审计功能"""
    with grpc.insecure_channel('127.0.0.1:50051') as channel:
        stub = auditor_pb2_grpc.DocumentAuditorStub(channel)
        
        # 创建测试请求
        section = auditor_pb2.Section()
        section.section_id = 1
        section.type = "abstract"
        section.text = "本文研究了人工智能技术的应用。"
        
        metadata = auditor_pb2.DocumentMetadata()
        metadata.title = "测试文档"
        metadata.page_count = 10
        
        parsed_data = auditor_pb2.ParsedData()
        parsed_data.doc_id = "test_doc_1"
        parsed_data.metadata.CopyFrom(metadata)
        parsed_data.sections.append(section)
        
        # 添加一个参考文献
        reference = auditor_pb2.Reference()
        reference.ref_id = "ref1"
        reference.raw_text = "Zhang, W. (2023). Advanced AI Methods. Journal of AI, 1(1), 1-10."
        parsed_data.references.append(reference)
        
        request = auditor_pb2.AuditRequest()
        request.data.CopyFrom(parsed_data)
        
        try:
            response = stub.AuditRules(request)
            print(f"规则审计完成，发现问题数量: {len(response.issues)}")
            for issue in response.issues:
                print(f"  - 代码: {issue.code}, 消息: {issue.message}, 严重程度: {issue.severity}")
            print(f"影响分数: {response.score_impact}")
        except Exception as e:
            print(f"规则审计测试失败: {e}")


if __name__ == "__main__":
    print("开始测试gRPC服务...")
    
    print("\n1. 测试语义分析功能:")
    test_semantic_analysis()
    
    print("\n2. 测试规则审计功能:")
    test_audit_rules()
    
    print("\n测试完成!")