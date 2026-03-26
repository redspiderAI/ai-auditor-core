from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
import json

app = Flask(__name__)

@app.route('/v1/quick-audit', methods=['POST'])
def quick_audit():
    # 获取上传的文件
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400
    
    file = request.files['file']
    filename = secure_filename(file.filename)
    
    # 读取文件内容
    content = file.read()
    text_content = content.decode('utf-8', errors='ignore')
    
    # 模拟AI审查结果
    mock_issues = [
        {
            "issue_id": "AI_001",
            "type": "TYPO",
            "severity": "HIGH",
            "original_text": "在研究过层中，我们发现...",
            "suggested_text": "在研究过程中，我们发现...",
            "reason": "检测到疑似错别字：'过层' -> '过程'。",
            "location_hint": "正文第一段附近"
        },
        {
            "issue_id": "AI_002",
            "type": "STYLE",
            "severity": "MEDIUM",
            "original_text": "由于这个实验结果非常的好，所以我们打算...",
            "suggested_text": "实验结果表现优异，拟采取...",
            "reason": "学术表达应避免使用口语化词汇（如：非常的好）。",
            "location_hint": "实验结果章节"
        }
    ]
    
    response_data = {
        "task_id": filename or "quick_audit",
        "status": "COMPLETED",
        "timestamp": "2026-03-10T19:45:00Z",
        "summary": {
            "total_issues": len(mock_issues),
            "score": 85.5,
            "mode": "EMERGENCY_AI_ONLY",
            "message": "当前处于快速 AI 审查模式，物理格式检查（字号/行距）已跳过。"
        },
        "results": mock_issues
    }
    
    return jsonify(response_data)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8123, debug=True)