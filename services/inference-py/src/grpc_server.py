"""增强版 gRPC 服务：实现 DocumentAuditorServicer 并启动服务器

使用方法（在 services/inference-py 下）：

```powershell
# 通过 uv 运行
uv run -m src.grpc_server

```
"""
from concurrent import futures
import logging
import time

import grpc

from src.protos import auditor_pb2, auditor_pb2_grpc
from src.semantic_detection import ComprehensiveSemanticDetector
from src.fact_checking import ReferenceFactChecker
from src.logic_consistency import LogicConsistencyChecker
from src.sliding_window_processor import run_sliding_window_analysis
from src.config import settings


class DocumentAuditorServicer(auditor_pb2_grpc.DocumentAuditorServicer):
    """增强版实现：集成语义检测、文献校验和逻辑一致性检查功能。"""

    def __init__(self):
        # 使用配置文件中的API密钥
        api_key = settings.dashscope_api_key

        # 初始化各检测器
        self.semantic_detector = ComprehensiveSemanticDetector(api_key=api_key)
        self.fact_checker = ReferenceFactChecker(api_key=api_key)
        self.consistency_checker = LogicConsistencyChecker(api_key=api_key)

    def ParseDocument(self, request, context):
        """解析文档（保留原有功能）"""
        metadata = auditor_pb2.DocumentMetadata(
            title=f"parsed:{request.file_path}",
            page_count=0,
            margin_top=0.0,
            margin_bottom=0.0,
        )
        parsed = auditor_pb2.ParsedData(
            doc_id=(request.file_path or "unknown"),
            metadata=metadata,
        )
        return parsed

    def AuditRules(self, request, context):
        """规则审计（增强功能）"""
        # 从请求中提取数据
        parsed_data = request.data

        # 提取章节信息
        sections = []
        for section in parsed_data.sections:
            sections.append({
                'section_id': section.section_id,
                'type': section.type,
                'text': section.text,
                'level': section.level
            })

        # 提取参考文献
        references = []
        for ref in parsed_data.references:
            references.append(ref.raw_text)

        # 初始化响应
        resp = auditor_pb2.AuditResponse()

        # 如果有章节内容，进行语义检测
        if sections:
            # 使用滑动窗口处理长文本
            import asyncio
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

            try:
                issues = loop.run_until_complete(
                    run_sliding_window_analysis(sections, api_key=settings.dashscope_api_key)
                )
                resp.issues.extend(issues)
            finally:
                loop.close()

        # 如果有参考文献，进行真实性校验
        if references:
            import asyncio
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

            try:
                ref_results = loop.run_until_complete(
                    self.fact_checker.check_references(references)
                )

                # 将参考文献检查结果转换为Issue格式
                for result in ref_results:
                    if not result.is_valid:
                        for issue in result.issues:
                            resp.issues.append(issue)
            finally:
                loop.close()

        # 计算影响分数（基于问题数量和严重程度）
        high_severity_count = sum(1 for issue in resp.issues if issue.severity == 3)  # HIGH
        medium_severity_count = sum(1 for issue in resp.issues if issue.severity == 2)  # MEDIUM
        low_severity_count = sum(1 for issue in resp.issues if issue.severity == 1)  # LOW

        # 计算加权影响分数 (0.0-1.0)
        total_weight = high_severity_count * 0.7 + medium_severity_count * 0.3 + low_severity_count * 0.1
        resp.score_impact = min(total_weight / max(len(resp.issues), 1), 1.0) if resp.issues else 0.0

        return resp

    def AnalyzeSemantics(self, request, context):
        """语义分析（增强功能）"""
        resp = auditor_pb2.AuditResponse()

        # 从请求中提取章节
        sections = []
        for section in request.sections:
            sections.append({
                'section_id': section.section_id,
                'type': section.type,
                'text': section.text,
                'level': section.level
            })

        # 对每个章节进行语义检测
        all_issues = []
        for section in sections:
            text = section.get('text', '')
            section_id = section.get('section_id', 0)

            # 使用语义检测器检测问题
            section_issues = self.semantic_detector.detect_issues(text)

            # 更新section_id到当前实际章节ID
            for issue in section_issues:
                issue.section_id = section_id

            all_issues.extend(section_issues)

        # 添加检测到的问题到响应
        resp.issues.extend(all_issues)

        # 计算影响分数
        high_severity_count = sum(1 for issue in resp.issues if issue.severity == 3)  # HIGH
        medium_severity_count = sum(1 for issue in resp.issues if issue.severity == 2)  # MEDIUM
        low_severity_count = sum(1 for issue in resp.issues if issue.severity == 1)  # LOW

        # 计算加权影响分数 (0.0-1.0)
        total_weight = high_severity_count * 0.7 + medium_severity_count * 0.3 + low_severity_count * 0.1
        resp.score_impact = min(total_weight / max(len(resp.issues), 1), 1.0) if resp.issues else 0.0

        return resp


def serve(host: str = "0.0.0.0", port: int = 50051) -> None:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    auditor_pb2_grpc.add_DocumentAuditorServicer_to_server(DocumentAuditorServicer(), server)
    bind_addr = f"{host}:{port}"
    server.add_insecure_port(bind_addr)
    server.start()
    logging.info("gRPC server started on %s", bind_addr)
    try:
        while True:
            time.sleep(60)
    except KeyboardInterrupt:
        logging.info("Shutting down gRPC server")
        server.stop(0)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    serve()
