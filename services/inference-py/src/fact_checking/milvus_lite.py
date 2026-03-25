"""Milvus Lite bootstrap helpers for fact checking."""
import os
import shutil
import socket
import tempfile
import time
from pathlib import Path
from typing import Optional

from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    connections,
    utility,
)

EASYLOGGING_CONTENT = """* GLOBAL:
    FORMAT               = "%datetime | %level | %msg"
    FILENAME             = "logs/milvus.log"
    ENABLED              = true
    TO_FILE              = true
    TO_STANDARD_OUTPUT   = true
    SUBSECOND_PRECISION  = 3
    PERFORMANCE_TRACKING = false
    MAX_LOG_FILE_SIZE    = 51200000
    LOG_FLUSH_THRESHOLD  = 100

* INFO:
    FORMAT = "%datetime | %level | %msg"

* WARNING:
    FORMAT = "%datetime | %level | %msg"

* ERROR:
    FORMAT = "%datetime | %level | %msg"

* DEBUG:
    FORMAT = "%datetime | %level | %msg"
"""

MILVUS_COLLECTION_NAME = "academic_papers"
EMBEDDING_DIM = 384


def _set_loopback_env() -> None:
    os.environ.setdefault("MILVUS_LOCAL_IP", "127.0.0.1")
    os.environ.setdefault("LOCAL_IP", "127.0.0.1")


def _cleanup_run_dir(data_dir: Path) -> None:
    run_dir = data_dir / "run"
    if run_dir.exists():
        shutil.rmtree(run_dir, ignore_errors=True)


def _port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            return True
    return False


def _wait_until_ready(uri: str, retries: int = 180, interval: float = 1.0) -> None:
    for _ in range(retries):
        try:
            connections.connect(alias="wait", uri=uri)
            utility.list_collections(using="wait")
            connections.disconnect(alias="wait")
            return
        except Exception:
            time.sleep(interval)
    raise TimeoutError("Milvus Lite not ready after waiting for startup.")


def _connect_with_retry(uri: str, alias: str = "default", retries: int = 60, interval: float = 1.0) -> None:
    last_exc: Optional[Exception] = None
    for _ in range(retries):
        try:
            connections.connect(alias=alias, uri=uri)
            return
        except Exception as exc:  # pragma: no cover - network/runtime failure
            last_exc = exc
            time.sleep(interval)
    raise RuntimeError(f"Milvus connect retry exceeded ({retries} attempts): {last_exc}")


def _get_default_server():
    try:
        from milvus import default_server  # type: ignore
    except ImportError as exc:  # pragma: no cover - import guard
        raise RuntimeError("milvus package is required to start Milvus Lite; install 'milvus'.") from exc
    return default_server


def start_milvus_lite(base_dir: Optional[Path] = None) -> str:
    """Start embedded Milvus Lite and return its URI."""
    default_base = Path(__file__).resolve().parents[2] / "milvus_data"
    auto_dir = Path(tempfile.gettempdir()) / "milvus-lite" / str(os.getpid())
    data_dir = base_dir or default_base
    tried_auto = False

    attempt = 0
    while attempt < 2:
        attempt += 1
        # If default dir is locked, fall back to a temp dir on next attempt.
        if not data_dir.exists():
            try:
                data_dir.mkdir(parents=True, exist_ok=True)
            except PermissionError:
                if base_dir is None:
                    data_dir = auto_dir
                    tried_auto = True
                    continue
                raise
        else:
            if base_dir is None:
                try:
                    shutil.rmtree(data_dir, ignore_errors=True)
                except Exception:
                    pass
                if not data_dir.exists():
                    data_dir.mkdir(parents=True, exist_ok=True)

        _set_loopback_env()
        _cleanup_run_dir(data_dir)

        configs_dir = data_dir / "configs"
        configs_dir.mkdir(parents=True, exist_ok=True)
        logs_dir = data_dir / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)
        easylogging_path = configs_dir / "easylogging.yaml"
        easylogging_path.write_text(EASYLOGGING_CONTENT, encoding="utf-8")

        default_server = _get_default_server()
        # Stop any previous instance before reconfiguring base_dir.
        try:
            default_server.stop()
        except Exception:
            pass
        default_server.set_base_dir(str(data_dir))

        for port in (19530, 40000):
            if _port_in_use(port):
                print(f"Warning: port {port} appears to be in use before Milvus start.")

        try:
            default_server.start()
            uri = getattr(default_server, "uri", "http://127.0.0.1:19530")
            _wait_until_ready(uri, retries=180, interval=1.0)
            print(f"Milvus Lite started at {uri}, data dir: {data_dir}")
            return uri
        except Exception as exc:
            print(f"Milvus Lite start failed (attempt {attempt}): {exc}")
            try:
                default_server.stop()
            except Exception:
                pass
            # On retry, nuke data dir to avoid corrupted meta
            shutil.rmtree(data_dir, ignore_errors=True)
            if base_dir is None and not tried_auto:
                data_dir = auto_dir
                tried_auto = True
            if attempt >= 2:
                raise


def ensure_collection(uri: str, collection_name: str = MILVUS_COLLECTION_NAME, dim: int = EMBEDDING_DIM) -> None:
    """Ensure target collection exists with expected schema and index."""
    _connect_with_retry(uri, alias="default", retries=60, interval=1.0)

    if utility.has_collection(collection_name):
        return

    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="title", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="authors", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="year", dtype=DataType.VARCHAR, max_length=16),
        FieldSchema(name="journal", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="reference", dtype=DataType.VARCHAR, max_length=2000),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dim),
    ]

    schema = CollectionSchema(fields, description="Academic papers embeddings")
    coll = Collection(collection_name, schema)
    coll.create_index(
        field_name="embedding",
        index_params={"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}},
    )
    coll.load()
    print(f"Collection {collection_name} created and loaded.")


def stop_milvus_lite() -> None:
    try:
        default_server = _get_default_server()
        default_server.stop()
    except Exception:
        pass
