"""综合语义检测器
结合规则基础检测和LLM检测的优势
"""
from typing import List
from ..protos.auditor_pb2 import Issue
from .semantic_detector import SemanticDetector
from .llm_detector import LLMDetector
from ..config import settings


class ComprehensiveSemanticDetector:
    """综合语义检测器"""
    
    def __init__(self, api_key: str = None):
        """
        初始化综合检测器

        Args:
            api_key: 通义千问API密钥，如果提供则启用LLM检测
        """
        # 优先使用传入的API密钥，否则使用配置文件中的
        effective_api_key = api_key or settings.dashscope_api_key

        self.rule_based_detector = SemanticDetector()
        self.llm_detector = LLMDetector(api_key=effective_api_key) if effective_api_key else None
    
    def detect_issues(self, text: str) -> List[Issue]:
        """
        综合检测文本中的问题
        
        Args:
            text: 待检测的文本
            
        Returns:
            List[Issue]: 检测到的问题列表
        """
        all_issues = []
        
        # 1. 使用规则基础检测器
        rule_result = self.rule_based_detector.detect_issues(text)
        all_issues.extend(rule_result.issues)
        
        # 2. 如果有API密钥，使用LLM检测器
        if self.llm_detector:
            llm_result = self.llm_detector.detect_semantic_errors(text)
            all_issues.extend(llm_result.issues)
        
        return all_issues