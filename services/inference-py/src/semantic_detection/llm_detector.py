"""基于大语言模型的语义检测器
使用Qwen模型进行更深层次的语义错误检测
"""
import json
import re
from typing import List, Optional
from dataclasses import dataclass

try:
    import dashscope  # 阿里云通义千问SDK
    DASHSCOPE_AVAILABLE = True
except ImportError:
    DASHSCOPE_AVAILABLE = False

from ..protos.auditor_pb2 import Issue, Severity


@dataclass
class LLMAnalysisResult:
    """LLM分析结果数据类"""
    issues: List[Issue]
    raw_response: str


class LLMDetector:
    """基于大语言模型的语义检测器"""
    
    def __init__(self, api_key: Optional[str] = None, model: str = "qwen-max"):
        """
        初始化LLM检测器
        
        Args:
            api_key: 通义千问API密钥
            model: 使用的模型名称，默认为qwen-max
        """
        self.api_key = api_key
        self.model = model
        if DASHSCOPE_AVAILABLE and api_key:
            dashscope.api_key = api_key
    
    def detect_semantic_errors(self, text: str, max_length: int = 2000) -> LLMAnalysisResult:
        """
        使用LLM检测语义错误
        
        Args:
            text: 待检测的文本
            max_length: 单次请求最大长度，超过则分段处理
            
        Returns:
            LLMAnalysisResult: 包含检测到的问题和原始响应
        """
        if not DASHSCOPE_AVAILABLE:
            # 如果没有dashscope，返回空结果
            return LLMAnalysisResult(issues=[], raw_response="DashScope SDK not available")
        
        if not self.api_key:
            return LLMAnalysisResult(issues=[], raw_response="API Key not provided")
        
        # 如果文本太长，进行分段处理
        if len(text) > max_length:
            segments = self._split_text(text, max_length)
            all_issues = []
            all_responses = []
            
            for segment in segments:
                result = self._analyze_segment(segment)
                all_issues.extend(result.issues)
                all_responses.append(result.raw_response)
            
            return LLMAnalysisResult(
                issues=all_issues,
                raw_response="\n".join(all_responses)
            )
        else:
            return self._analyze_segment(text)
    
    def _split_text(self, text: str, max_length: int) -> List[str]:
        """将长文本按句子分割成多个段落"""
        # 按句号、问号、感叹号分割
        sentences = re.split(r'([。！？.!?])', text)
        # 重新组合句子和标点
        sentences = [sentences[i] + (sentences[i+1] if i+1 < len(sentences) else '') 
                     for i in range(0, len(sentences), 2)]
        
        segments = []
        current_segment = ""
        
        for sentence in sentences:
            if len(current_segment + sentence) <= max_length:
                current_segment += sentence
            else:
                if current_segment:
                    segments.append(current_segment)
                current_segment = sentence
        
        if current_segment:
            segments.append(current_segment)
        
        return segments
    
    def _analyze_segment(self, text: str) -> LLMAnalysisResult:
        """分析单个文本段落"""
        prompt = self._build_prompt(text)
        
        try:
            response = dashscope.Generation.call(
                model=self.model,
                prompt=prompt,
                top_p=0.8,
                temperature=0.5,
                max_tokens=2000,
                result_format='json'
            )
            
            if response.status_code == 200:
                raw_response = response.output.text
                
                # 解析JSON响应
                issues = self._parse_llm_response(raw_response)
                
                return LLMAnalysisResult(issues=issues, raw_response=raw_response)
            else:
                # 如果API调用失败，返回空结果
                return LLMAnalysisResult(issues=[], raw_response=f"API Error: {response}")
                
        except Exception as e:
            return LLMAnalysisResult(issues=[], raw_response=f"Exception: {str(e)}")
    
    def _build_prompt(self, text: str) -> str:
        """构建发送给LLM的提示词"""
        prompt_parts = [
            "# Role",
            "你是一位精通中文学术规范的审稿专家，专门负责检测论文中的细微语义错误。",
            "",
            "# Task",
            "输入为一段论文正文。请输出以下 JSON：",
            "{",
            '  "issues": [',
            "    {",
            '      "type": "TYPO | PUNCTUATION | STYLE | SEMANTIC",',
            '      "original": "错误文本",',
            '      "suggested": "修改后的文本",',
            '      "reason": "解释为什么此处不合规",',
            '      "severity": 1-5',
            "    }",
            "  ]",
            "}",
            "",
            "# Knowledge Base (Qwen Specific)",
            "- 遵循《通用规范汉字表》",
            "- 遵循 GB/T 15834 标点符号用法",
            "- 识别理工科、人文社科的不同用词习惯",
            "",
            "# Input Text",
            text,
            "",
            "# Output (JSON only)"
        ]

        return "\n".join(prompt_parts)
    
    def _parse_llm_response(self, response: str) -> List[Issue]:
        """解析LLM的JSON响应"""
        issues = []
        
        # 尝试找到JSON部分
        json_match = re.search(r'\{.*\}', response, re.DOTALL)
        if json_match:
            try:
                json_str = json_match.group()
                data = json.loads(json_str)
                
                if 'issues' in data:
                    for issue_data in data['issues']:
                        issue = Issue()
                        
                        # 映射类型到严重程度
                        type_mapping = {
                            'TYPO': Severity.MEDIUM,
                            'PUNCTUATION': Severity.LOW,
                            'STYLE': Severity.MEDIUM,
                            'SEMANTIC': Severity.HIGH
                        }
                        
                        issue.code = issue_data.get('type', 'UNKNOWN')
                        issue.message = issue_data.get('reason', '')
                        issue.original_snippet = issue_data.get('original', '')
                        issue.suggestion = issue_data.get('suggested', '')
                        issue.severity = type_mapping.get(issue_data.get('type', ''), Severity.LOW)
                        
                        # 设置默认严重程度值
                        severity_val = issue_data.get('severity', 3)
                        if severity_val >= 4:
                            issue.severity = Severity.CRITICAL
                        elif severity_val >= 3:
                            issue.severity = Severity.HIGH
                        elif severity_val >= 2:
                            issue.severity = Severity.MEDIUM
                        else:
                            issue.severity = Severity.LOW
                        
                        issues.append(issue)
                        
            except json.JSONDecodeError:
                # 如果JSON解析失败，尝试简单解析
                pass
        
        return issues