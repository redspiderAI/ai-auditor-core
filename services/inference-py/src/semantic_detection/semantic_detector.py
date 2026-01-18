"""学术语境高风险项检测模块
实现错别字、标点规范、学术风格检查等功能
"""
import re
from typing import List, Dict, Any
from dataclasses import dataclass

from ..protos.auditor_pb2 import Issue, Severity


@dataclass
class DetectionResult:
    """检测结果数据类"""
    issues: List[Issue]
    suggestions: List[Dict[str, Any]]


class SemanticDetector:
    """学术语境高风险项检测器"""

    def __init__(self):
        # 形近字错误映射
        self.typo_corrections = {
            "粘度": "黏度",
            "份量": "分量",
            "份儿": "分儿",
            "听闻": "听闻",  # 在某些语境下可能需要更正
        }

        # 半角标点符号模式
        self.half_punct_pattern = r'[!@#$%^&*()_+\-=\[\]{};\':"\\|,.<>\/?]'

        # 半角到全角字符映射
        self.half_to_full_map = {
            '!': '！', '"': '＂', '#': '＃', '$': '＄', '%': '％',
            '&': '＆', "'": '＇', '(': '（', ')': '）', '*': '＊',
            '+': '＋', ',': '，', '-': '－', '.': '．', '/': '／',
            ':': '：', ';': '；', '<': '＜', '=': '＝', '>': '＞',
            '?': '？', '@': '＠', '[': '［', '\\': '＼', ']': '］',
            '^': '＾', '_': '＿', '`': '｀', '{': '｛', '|': '｜',
            '}': '｝', '~': '～'
        }

        # 口语化词汇映射
        self.colloquialisms = {
            "听说": "据报道",
            "据说": "研究表明",
            "特别好": "具有显著优势",
            "很好": "表现优异",
            "不错": "效果良好",
            "很牛": "性能卓越",
            "厉害": "表现出色",
            "东西": "组件",
            "玩意儿": "工具",
            "搞": "进行",
            "弄": "执行",
            "做": "实施",
        }

    def detect_issues(self, text: str) -> DetectionResult:
        """检测文本中的问题
        
        Args:
            text: 待检测的文本
            
        Returns:
            DetectionResult: 包含检测到的问题和建议
        """
        issues = []
        suggestions = []

        # 1. 检测错别字
        typo_issues = self._detect_typos(text)
        issues.extend(typo_issues)
        
        # 2. 检测标点规范
        punctuation_issues = self._detect_punctuation_errors(text)
        issues.extend(punctuation_issues)
        
        # 3. 检测学术风格问题
        style_issues = self._detect_style_errors(text)
        issues.extend(style_issues)

        return DetectionResult(issues=issues, suggestions=suggestions)

    def _detect_typos(self, text: str) -> List[Issue]:
        """检测错别字"""
        issues = []
        for wrong, correct in self.typo_corrections.items():
            if wrong in text:
                # 找到所有出现位置
                for match in re.finditer(re.escape(wrong), text):
                    issue = Issue()
                    issue.code = "TYPO"
                    issue.message = f"形近字错误: '{wrong}' 应为 '{correct}'"
                    issue.original_snippet = wrong
                    issue.suggestion = correct
                    issue.severity = Severity.MEDIUM
                    issues.append(issue)
        return issues

    def _detect_punctuation_errors(self, text: str) -> List[Issue]:
        """检测标点符号错误"""
        issues = []
        # 查找中文文本中的半角标点
        matches = re.finditer(self.half_punct_pattern, text)
        for match in matches:
            char = match.group()
            pos = match.start()
            
            # 检查上下文是否为中文字符
            prev_char = text[pos-1] if pos > 0 else ""
            next_char = text[pos+1] if pos < len(text)-1 else ""
            
            # 如果周围是中文字符，则认为是标点错误
            if self._is_chinese_char(prev_char) or self._is_chinese_char(next_char):
                issue = Issue()
                issue.code = "PUNCTUATION"
                issue.message = f"应使用全角标点: '{char}' 应为对应全角符号"
                issue.original_snippet = char
                issue.suggestion = self._to_full_width(char)
                issue.severity = Severity.LOW
                issues.append(issue)
        return issues

    def _detect_style_errors(self, text: str) -> List[Issue]:
        """检测学术风格问题"""
        issues = []
        for colloquial, formal in self.colloquialisms.items():
            if colloquial in text:
                for match in re.finditer(re.escape(colloquial), text):
                    issue = Issue()
                    issue.code = "STYLE"
                    issue.message = f"口语化表达: '{colloquial}' 建议改为 '{formal}'"
                    issue.original_snippet = colloquial
                    issue.suggestion = formal
                    issue.severity = Severity.MEDIUM
                    issues.append(issue)
        return issues

    def _is_chinese_char(self, char: str) -> bool:
        """判断字符是否为中文字符"""
        if not char:
            return False
        return '\u4e00' <= char <= '\u9fff'

    def _to_full_width(self, char: str) -> str:
        """将半角字符转换为全角字符"""
        return self.half_to_full_map.get(char, char)