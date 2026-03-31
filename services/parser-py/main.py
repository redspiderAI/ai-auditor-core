import argparse

import uvicorn

from src.http_app import create_app
from src.utils.document_processor import run_document_processing


def main():
    parser = argparse.ArgumentParser(description="parser-py entrypoint")
    parser.add_argument("--mode", choices=["fastapi", "process", "grpc"], default="fastapi")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8001)
    args = parser.parse_args()

    if args.mode == "process":
        run_document_processing()
        return

    if args.mode == "grpc":
        from src.grpc.grpc_service import serve

        serve(host=args.host, port=args.port)
        return

    app = create_app()
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
