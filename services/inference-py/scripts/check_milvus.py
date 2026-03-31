import json
import sys
import traceback

out = {"status": "unknown"}
try:
    # 获取配置
    try:
        from src.config import settings
        host = getattr(settings, 'milvus_host', '127.0.0.1')
        port = getattr(settings, 'milvus_port', 19530)
    except Exception:
        import os
        host = os.environ.get('MILVUS_HOST', '127.0.0.1')
        port = int(os.environ.get('MILVUS_PORT', '19530'))

    out['host'] = host
    out['port'] = int(port)

    try:
        from pymilvus import connections, utility
    except Exception as e:
        out['status'] = 'pymilvus_missing'
        out['error'] = str(e)
        print(json.dumps(out, ensure_ascii=False))
        sys.exit(0)

    try:
        connections.connect(host=host, port=str(port))
        out['status'] = 'connected'
        try:
            cols = utility.list_collections()
            out['collections'] = cols
            out['has_academic_papers'] = 'academic_papers' in cols
        except Exception as e:
            out['collections_error'] = str(e)
    except Exception as e:
        out['status'] = 'connect_failed'
        out['error'] = str(e)

except Exception as e:
    out['status'] = 'error'
    out['error'] = str(e)
    out['trace'] = traceback.format_exc()

print(json.dumps(out, ensure_ascii=False))
