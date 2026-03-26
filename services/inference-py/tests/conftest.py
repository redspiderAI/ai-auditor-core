import sys
from pathlib import Path
import os

# Ensure project root is importable when running tests via `uv run` or plain python
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Disable embedded Milvus Lite during unit tests to avoid heavy startup and permission issues
os.environ.setdefault("MILVUS_LITE_DISABLED", "1")
