import time
from src.fact_checking.milvus_lite import (
    ensure_collection,
    start_milvus_lite,
    stop_milvus_lite,
)


def main() -> None:
    uri = start_milvus_lite()
    ensure_collection(uri)
    print("Milvus Lite is running. Press Ctrl+C to stop.")

    try:
        while True:
            time.sleep(60)
    except KeyboardInterrupt:
        print("Stopping Milvus Lite...")
        stop_milvus_lite()


if __name__ == "__main__":
    main()

