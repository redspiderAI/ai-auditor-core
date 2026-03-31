import grpc
from concurrent import futures
import time
from pathlib import Path

# 导入生成的 protobuf 模块
import src.auditor_pb2 as auditor_pb2
import src.auditor_pb2_grpc as auditor_pb2_grpc

from src.core.parser import DocxParser
from src.core.pdf_parser import PdfParser
from src.utils.comment_writer import inject_comments, ErrorItem

class DocumentAuditorServicer(auditor_pb2_grpc.DocumentAuditorServicer):
    def __init__(self):
        self.docx_parser = DocxParser()
        self.pdf_parser = PdfParser()
    
    def ParseDocument(self, request, context):
        file_path = request.file_path
        
        # 根据文件扩展名选择解析器
        if file_path.endswith('.docx'):
            result = self.docx_parser.parse(Path(file_path))
        elif file_path.endswith('.pdf'):
            result = self.pdf_parser.parse(Path(file_path))
        else:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details(f"Unsupported file format: {file_path}")
            return auditor_pb2.ParsedData()
        
        # 构建响应
        parsed_data = auditor_pb2.ParsedData()
        # 修复 f-string 语法错误，将 replace 操作移到外部
        normalized_path = file_path.replace('/', '_').replace('\\', '_')
        doc_id = f"doc_{normalized_path}"
        parsed_data.doc_id = doc_id
        
        # 设置元数据
        metadata = result['metadata']
        parsed_data.metadata.title = metadata.get('title', '')
        parsed_data.metadata.page_count = metadata.get('total_pages', 0)
        parsed_data.metadata.margin_top = 1.0
        parsed_data.metadata.margin_bottom = 1.0
        
        # 添加 sections
        for section in result['sections']:
            section_proto = parsed_data.sections.add()
            section_proto.section_id = section.id
            
            if hasattr(section.element_type, 'level'):
                section_proto.type = 'heading'
                section_proto.level = section.element_type.level
            elif section.element_type == 'Table':
                section_proto.type = 'table'
                section_proto.level = 0
            else:
                section_proto.type = 'paragraph'
                section_proto.level = 0
            
            section_proto.text = section.raw_text
            
            # 添加属性
            for key, value in section.formatting.items():
                section_proto.props[key] = value

            # 回写定位：保留 xml_path 与 offset，供 Go 网关和后续批注使用
            section_proto.props['xml_path'] = section.xml_path
            section_proto.props['offset'] = str(section.offset)

            # 位置坐标（来自解析阶段计算的 positions 映射）
            position = result.get('positions', {}).get(section.id)
            if position:
                section_proto.props['position.x'] = str(position.get('x', ''))
                section_proto.props['position.y'] = str(position.get('y', ''))
                section_proto.props['position.width'] = str(position.get('width', ''))
                section_proto.props['position.height'] = str(position.get('height', ''))
                section_proto.props['position.page_number'] = str(position.get('page_number', ''))
        
        return parsed_data
    
    def AuditRules(self, request, context):
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details("AuditRules not implemented in Python service")
        return auditor_pb2.AuditResponse()
    
    def AnalyzeSemantics(self, request, context):
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details("AnalyzeSemantics not implemented in Python service")
        return auditor_pb2.AuditResponse()
    
    def InjectAnnotations(self, request, context):
        file_path = request.file_path
        output_path = f"{file_path}.annotated.docx"
        
        # 转换 issues 为 ErrorItem
        error_items = []
        for issue in request.issues:
            error_item = ErrorItem(
                paragraph_index=issue.section_id,
                comment=f"{issue.code}: {issue.message}\nSuggestion: {issue.suggestion}"
            )
            error_items.append(error_item)
        
        # 注入批注
        success = inject_comments(file_path, error_items, output_path, "AI Auditor")
        
        if success:
            response = auditor_pb2.InjectResponse()
            response.annotated_path = output_path
            return response
        else:
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details("Failed to inject annotations")
            return auditor_pb2.InjectResponse()

def serve(host: str = '0.0.0.0', port: int = 50051):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    auditor_pb2_grpc.add_DocumentAuditorServicer_to_server(DocumentAuditorServicer(), server)
    bind = f"{host}:{port}"
    server.add_insecure_port(bind)  # 监听所有网络接口
    server.start()
    print(f"🚀 Starting gRPC server on {bind}")
    try:
        while True:
            time.sleep(86400)  # 一天
    except KeyboardInterrupt:
        server.stop(0)
