"""长文本逻辑一致性扫描模块
检测文章前后表述一致性，包括摘要-结论对齐、术语一致性等
"""
import re
from typing import List, Dict, Any, Optional
from collections import defaultdict
from dataclasses import dataclass

try:
    import dashscope
    DASHSCOPE_AVAILABLE = True
except ImportError:
    DASHSCOPE_AVAILABLE = False

from ..protos.auditor_pb2 import Issue, Severity
from ..config import settings


@dataclass
class ConsistencyCheckResult:
    """一致性检查结果"""
    issues: List[Issue]
    term_usage: Dict[str, List[int]]  # 术语在各章节的使用情况
    summary_alignment_score: float  # 摘要对齐分数


class LogicConsistencyChecker:
    """长文本逻辑一致性检查器"""
    
    def __init__(self, api_key: Optional[str] = None):
        """
        初始化一致性检查器

        Args:
            api_key: 通义千问API密钥
        """
        # 优先使用传入的API密钥，否则使用配置文件中的
        self.api_key = api_key or settings.dashscope_api_key
        if DASHSCOPE_AVAILABLE and self.api_key:
            dashscope.api_key = self.api_key

        # 存储全文中使用的术语
        self.terms = defaultdict(list)
        # 存储摘要和结论部分
        self.summary_section = ""
        self.conclusion_section = ""
    
    async def check_consistency(self, sections: List[Dict[str, Any]]) -> ConsistencyCheckResult:
        """
        检查长文本的逻辑一致性
        
        Args:
            sections: 文档章节列表，每个章节包含id、type、text等信息
            
        Returns:
            ConsistencyCheckResult: 一致性检查结果
        """
        issues = []
        self.terms.clear()
        self.summary_section = ""
        self.conclusion_section = ""
        
        # 分析各章节内容
        section_texts = {}
        for section in sections:
            sec_id = section.get('section_id', 0)
            sec_type = section.get('type', '').lower()
            sec_text = section.get('text', '')
            
            section_texts[sec_id] = sec_text
            
            # 识别摘要和结论部分
            if 'summary' in sec_type or 'abstract' in sec_type:
                self.summary_section = sec_text
            elif 'conclusion' in sec_type or 'conclude' in sec_type:
                self.conclusion_section = sec_text
        
        # 1. 检查术语一致性
        term_issues = self._check_term_consistency(section_texts)
        issues.extend(term_issues)
        
        # 2. 检查摘要-结论对齐
        alignment_issues = await self._check_summary_conclusion_alignment()
        issues.extend(alignment_issues)
        
        # 计算摘要对齐分数
        alignment_score = self._calculate_alignment_score()
        
        return ConsistencyCheckResult(
            issues=issues,
            term_usage=dict(self.terms),
            summary_alignment_score=alignment_score
        )
    
    def _check_term_consistency(self, section_texts: Dict[int, str]) -> List[Issue]:
        """检查术语一致性"""
        issues = []
        
        # 定义术语模式（例如：专业术语、缩写等）
        term_patterns = [
            r'\b[A-Z]{2,4}\b',  # 缩写词（2-4个大写字母）
            r'[\u4e00-\u9fff]{2,5}(?:算法|模型|方法|网络|系统)',  # 中文专业术语
            r'\b\w+(?:算法|method|model|network|system)\b',  # 英文专业术语
        ]
        
        for sec_id, text in section_texts.items():
            # 查找所有术语
            for pattern in term_patterns:
                matches = re.finditer(pattern, text, re.IGNORECASE)
                for match in matches:
                    term = match.group()
                    
                    # 检查是否首次出现
                    if term.lower() not in [t.lower() for t in self.terms.keys()]:
                        # 检查是否为缩写（全大写）
                        if term.isupper() and len(term) > 1:
                            # 检查是否已有全称
                            full_form_found = False
                            for existing_term in self.terms.keys():
                                if self._is_abbreviation_of(term, existing_term):
                                    full_form_found = True
                                    break
                            
                            if not full_form_found:
                                # 发现未定义的缩写
                                issue = Issue()
                                issue.code = "UNDEFINED_ABBREVIATION"
                                issue.message = f"章节 {sec_id} 中使用了未定义的缩写: '{term}'"
                                issue.original_snippet = term
                                issue.suggestion = f"首次使用时应给出全称，如 'XXX ({term})'"
                                issue.severity = Severity.HIGH
                                issues.append(issue)
                    
                    # 记录术语在哪个章节中使用
                    if sec_id not in self.terms[term]:
                        self.terms[term].append(sec_id)
        
        # 检查术语使用的一致性
        for term, sections in self.terms.items():
            if len(sections) > 1:
                # 术语在多个章节中使用，检查是否有不同的表达方式
                variations = self._find_term_variations(term, section_texts)
                if variations:
                    for variation in variations:
                        issue = Issue()
                        issue.code = "TERM_INCONSISTENCY"
                        issue.message = f"术语 '{term}' 存在不一致的表达方式: '{variation}'"
                        issue.original_snippet = variation
                        issue.suggestion = f"统一使用术语 '{term}'"
                        issue.severity = Severity.MEDIUM
                        issues.append(issue)
        
        return issues
    
    async def _check_summary_conclusion_alignment(self) -> List[Issue]:
        """检查摘要-结论对齐"""
        if not self.summary_section or not self.conclusion_section:
            return []
        
        issues = []
        
        if DASHSCOPE_AVAILABLE and self.api_key:
            # 使用LLM分析摘要和结论的一致性
            prompt = self._build_alignment_prompt()
            
            try:
                response = dashscope.Generation.call(
                    model="qwen-max",
                    prompt=prompt,
                    top_p=0.8,
                    temperature=0.5,
                    max_tokens=1000
                )
                
                if response.status_code == 200:
                    analysis = response.output.text
                    llm_issues = self._parse_alignment_result(analysis)
                    issues.extend(llm_issues)
                else:
                    # API调用失败，使用简单关键词匹配
                    simple_issues = self._simple_alignment_check()
                    issues.extend(simple_issues)
            except Exception:
                # 异常处理，使用简单关键词匹配
                simple_issues = self._simple_alignment_check()
                issues.extend(simple_issues)
        else:
            # 如果没有API密钥，使用简单关键词匹配
            simple_issues = self._simple_alignment_check()
            issues.extend(simple_issues)
        
        return issues
    
    def _simple_alignment_check(self) -> List[Issue]:
        """简单的摘要-结论对齐检查"""
        issues = []
        
        # 提取摘要中的关键词
        summary_keywords = set(re.findall(r'\b\w{4,}\b', self.summary_section.lower()))
        conclusion_keywords = set(re.findall(r'\b\w{4,}\b', self.conclusion_section.lower()))
        
        # 检查摘要中提到但在结论中缺失的主题
        missing_in_conclusion = summary_keywords - conclusion_keywords
        
        if missing_in_conclusion:
            issue = Issue()
            issue.code = "SUMMARY_CONCLUSION_MISMATCH"
            issue.message = f"摘要中提到但结论中缺失的主题: {', '.join(list(missing_in_conclusion)[:5])}"  # 只显示前5个
            issue.severity = Severity.HIGH
            issues.append(issue)
        
        return issues
    
    def _build_alignment_prompt(self) -> str:
        """构建摘要-结论对齐检查的提示词"""
        prompt_template = """# Role
你是一位专业的学术论文审核专家，负责检查论文摘要与结论之间的一致性。

# Task
请分析以下论文摘要和结论，判断它们在研究内容、主要发现和结论方面是否一致。

# Input
## 摘要
{summary}

## 结论
{conclusion}

# Output Instructions
请按照以下JSON格式输出你的分析结果：
{{
  "issues": [
    {{
      "type": "CONTENT_MISMATCH | FINDING_MISMATCH | CONCLUSION_MISMATCH | MISSING_ELEMENT",
      "description": "具体不一致之处",
      "severity": "HIGH | MEDIUM | LOW"
    }}
  ],
  "alignment_score": 0.0-1.0,
  "summary": "简要总结一致性情况"
}}

# Analysis
"""
        
        return prompt_template.format(
            summary=self.summary_section,
            conclusion=self.conclusion_section
        )
    
    def _parse_alignment_result(self, analysis: str) -> List[Issue]:
        """解析LLM的对齐分析结果"""
        import json
        import re
        
        issues = []
        
        # 尝试解析JSON响应
        json_match = re.search(r'\{.*\}', analysis, re.DOTALL)
        if json_match:
            try:
                data = json.loads(json_match.group())
                
                if 'issues' in data:
                    for issue_data in data['issues']:
                        issue = Issue()
                        issue.code = issue_data.get('type', 'ALIGNMENT_ISSUE')
                        issue.message = issue_data.get('description', '')
                        
                        severity_map = {
                            'HIGH': Severity.HIGH,
                            'MEDIUM': Severity.MEDIUM,
                            'LOW': Severity.LOW
                        }
                        issue.severity = severity_map.get(issue_data.get('severity', 'MEDIUM'), Severity.MEDIUM)
                        
                        issues.append(issue)
            except json.JSONDecodeError:
                pass
        
        return issues
    
    def _find_term_variations(self, term: str, section_texts: Dict[int, str]) -> List[str]:
        """查找术语的变体"""
        variations = []
        
        # 创建一个正则表达式来匹配类似的术语
        # 例如，对于"卷积神经网络"，可能的变体包括"CNN"、"convolutional neural network"等
        # base_term = re.escape(term)
        
        for text in section_texts.values():
            # 简单的变体检测：检查是否包含术语的一部分但形式不同
            # 这里可以扩展为更复杂的同义词/缩写匹配逻辑
            pass  # 当前实现中暂时跳过复杂变体检测
        
        return variations
    
    def _is_abbreviation_of(self, abbr: str, full_term: str) -> bool:
        """检查abbr是否为full_term的缩写"""
        # 简单的缩写匹配逻辑
        full_words = re.split(r'[\s\-_]', full_term)
        if len(abbr) == len(full_words):
            # 检查首字母缩写
            return all(word and word[0].upper() == abbr[i] for i, word in enumerate(full_words))
        elif len(abbr) == 1:
            # 检查是否为某个词的首字母
            return any(word and word.startswith(abbr.upper()) for word in full_words)
        else:
            # 更复杂的缩写匹配
            # 这里可以实现更精确的缩写匹配算法
            return False
    
    def _calculate_alignment_score(self) -> float:
        """计算摘要对齐分数"""
        if not self.summary_section or not self.conclusion_section:
            return 0.0
        
        # 简单的对齐分数计算：基于关键词重叠
        summary_keywords = set(re.findall(r'\b\w{4,}\b', self.summary_section.lower()))
        conclusion_keywords = set(re.findall(r'\b\w{4,}\b', self.conclusion_section.lower()))
        
        if not summary_keywords:
            return 0.0
        
        overlap = len(summary_keywords.intersection(conclusion_keywords))
        score = overlap / len(summary_keywords)
        
        return min(score, 1.0)  # 确保分数不超过1.0