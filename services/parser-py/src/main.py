import sys

from src.utils.document_processor import run_document_processing
from src.grpc.grpc_service import serve

def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'grpc':
        # 启动 gRPC 服务
        serve()
    elif len(sys.argv) > 1 and sys.argv[1] == 'process':
        # 处理文档
        run_document_processing()
    else:
        print("parser_py binary ready. Use 'process' to process documents.")
        print("Use 'grpc' to start the gRPC server on port 50051.")

if __name__ == '__main__':
    main()
