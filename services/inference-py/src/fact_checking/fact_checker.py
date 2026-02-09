"""参考文献真实性校验模块
实现文献检索与真伪比对功能（RAG）
"""
import re
from typing import List, Dict, Any, Optional
from dataclasses import dataclass

try:
    from pymilvus import connections
    MILVUS_AVAILABLE = True
except ImportError:
    MILVUS_AVAILABLE = False

try:
    import dashscope
    DASHSCOPE_AVAILABLE = True
except ImportError:
    DASHSCOPE_AVAILABLE = False

from ..protos.auditor_pb2 import Issue, Severity
from ..config import settings


@dataclass
class ReferenceCheckResult:
    """参考文献检查结果"""
    is_valid: bool
    issues: List[Issue]
    confidence_score: float
    details: Dict[str, Any]


class ReferenceFactChecker:
    """参考文献真实性校验器"""
    
    def __init__(self, api_key: Optional[str] = None, milvus_config: Optional[Dict] = None):
        """
        初始化参考文献校验器

        Args:
            api_key: 通义千问API密钥
            milvus_config: Milvus连接配置
        """
        # 优先使用传入的API密钥，否则使用配置文件中的
        self.api_key = api_key or settings.dashscope_api_key
        if DASHSCOPE_AVAILABLE and self.api_key:
            dashscope.api_key = self.api_key

        # 使用配置文件中的Milvus配置，或者使用传入的配置，或者使用默认值
        self.milvus_config = milvus_config or {
            "host": settings.milvus_host,
            "port": settings.milvus_port
        }

        if MILVUS_AVAILABLE:
            try:
                connections.connect(**self.milvus_config)
                self.milvus_connected = True
            except Exception:
                self.milvus_connected = False
        else:
            self.milvus_connected = False
    
    async def check_references(self, references: List[str]) -> List[ReferenceCheckResult]:
        """
        检查参考文献的真实性
        
        Args:
            references: 参考文献列表
            
        Returns:
            List[ReferenceCheckResult]: 检查结果列表
        """
        results = []
        
        for reference in references:
            result = await self._check_single_reference(reference)
            results.append(result)
        
        return results
    
    async def _check_single_reference(self, reference: str) -> ReferenceCheckResult:
        """检查单个参考文献"""
        # 提取参考文献的关键信息
        extracted_info = self._extract_reference_info(reference)
        
        # 如果Milvus可用，进行向量检索
        if self.milvus_connected:
            retrieved_docs = await self._retrieve_similar_documents(extracted_info)
        else:
            retrieved_docs = []
        
        # 使用LLM进行比对分析
        if DASHSCOPE_AVAILABLE and self.api_key:
            analysis_result = await self._analyze_with_llm(reference, retrieved_docs)
        else:
            # 如果没有LLM，仅基于提取的信息进行简单检查
            analysis_result = self._simple_analysis(reference, extracted_info)
        
        return analysis_result
    
    def _extract_reference_info(self, reference: str) -> Dict[str, str]:
        """从参考文献中提取关键信息"""
        info = {}
        
        # 提取标题（通常在引号或书名号内，也包括句点后的标题）
        title_match = re.search(r'[""''"'']([^""''"'']+)[""''"'']|\[([^]]+)\]|《([^》]+)》', reference)
        if title_match:
            info['title'] = title_match.group(1) or title_match.group(2) or title_match.group(3)
        else:
            # 尝试从句点后的文本中提取标题
            # 例如: Zhang, W., & Li, M. (2023). Advanced AI Methods in Modern Computing. Journal...
            # 提取 (2023). 和下一个句点之间的内容
            title_pattern = r'\(\d{4}\)\.\s*([^.]+)'
            title_match = re.search(title_pattern, reference)
            if title_match:
                title = title_match.group(1).strip()
                # 移除可能的期刊名部分
                if 'Journal' in title or 'Conference' in title:
                    # 如果匹配到了期刊名，则不将其视为标题
                    pass
                else:
                    info['title'] = title
        
        # 提取年份
        year_match = re.search(r'(?:\b\d{4}\b|出版时间[:：]\s*(\d{4}))', reference)
        if year_match:
            info['year'] = year_match.group(0).strip()
        
        # 提取期刊/会议名
        journal_match = re.search(r'in\s+([A-Za-z\s]+(?:[A-Z][a-z]*\s*)+)|发表在\s*([^(《"「]+)', reference)
        if journal_match:
            info['journal'] = (journal_match.group(1) or journal_match.group(2)).strip()
        
        # 提取作者
        author_match = re.search(r'^([^.,;]+)', reference)
        if author_match:
            authors = author_match.group(1).strip()
            # 简单过滤掉数字和特殊字符开头的部分
            if not re.match(r'^[\d\s\(\)]+', authors):
                info['authors'] = authors
        
        return info
    
    async def _retrieve_similar_documents(self, reference_info: Dict[str, str]) -> List[Dict[str, Any]]:
        """从Milvus中检索相似文档"""
        if not MILVUS_AVAILABLE or not self.milvus_connected:
            return []
        
        # 这里只是一个示例实现，实际应用中需要根据具体的Milvus集合结构来调整
        try:
            # 假设有一个名为"academic_papers"的集合
            collection_name = "academic_papers"
            
            # 检查集合是否存在
            from pymilvus import utility
            if not utility.has_collection(collection_name):
                return []
            
            # collection = Collection(collection_name)
            
            # # 构建查询条件（这里简化处理）
            # search_params = {
            #     "metric_type": "COSINE",
            #     "params": {"nprobe": 10}
            # }
            
            # 使用标题作为查询向量的来源（实际应用中需要嵌入模型）
            query_title = reference_info.get('title', '')
            if not query_title:
                return []
            
            # 注意：这只是一个示意性实现，实际需要嵌入模型生成向量
            # 并且需要预先建立好学术论文的向量索引
            results = []  # 实际应用中需要替换为真正的检索逻辑
            
            return results
        except Exception as e:
            print(f"Milvus检索出错: {e}")
            return []
    
    async def _analyze_with_llm(self, reference: str, retrieved_docs: List[Dict[str, Any]]) -> ReferenceCheckResult:
        """使用LLM分析参考文献真实性"""
        if not DASHSCOPE_AVAILABLE or not self.api_key:
            return self._simple_analysis(reference, self._extract_reference_info(reference))
        
        prompt = self._build_fact_check_prompt(reference, retrieved_docs)
        
        try:
            response = dashscope.Generation.call(
                model="qwen-long",  # 使用支持长文本的模型
                prompt=prompt,
                top_p=0.8,
                temperature=0.5,
                max_tokens=1000
            )
            
            if response.status_code == 200:
                analysis = response.output.text
                return self._parse_fact_check_result(analysis, reference)
            else:
                # API调用失败，返回默认结果
                return ReferenceCheckResult(
                    is_valid=False,
                    issues=[],
                    confidence_score=0.0,
                    details={"error": f"API Error: {response}"}
                )
        except Exception as e:
            # 异常处理，返回默认结果
            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = f"文献真实性检查出错: {str(e)}"
            issue.severity = Severity.HIGH
            
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"error": str(e)}
            )
    
    def _build_fact_check_prompt(self, reference: str, retrieved_docs: List[Dict[str, Any]]) -> str:
        """构建文献真实性检查的提示词"""
        prompt_template = """# Role
你是一位专业的学术文献审核专家，负责验证参考文献的真实性。

# Task
请对比用户提供的参考文献与检索到的真实文献信息，判断该参考文献是否真实存在。

# Input
## 用户提供的参考文献
{user_reference}

## 检索到的相关文献
{retrieved_docs}

# Output Instructions
请按照以下JSON格式输出你的分析结果：
{{
  "is_valid": true/false,
  "confidence_score": 0.0-1.0,
  "issues": [
    {{
      "type": "AUTHOR_MISMATCH | TITLE_MISMATCH | YEAR_MISMATCH | JOURNAL_MISMATCH | NOT_FOUND",
      "description": "具体问题描述",
      "severity": "HIGH | MEDIUM | LOW"
    }}
  ],
  "explanation": "简要说明判断依据"
}}

# Analysis
"""
        
        retrieved_text = "\n".join([
            f"- {doc.get('title', 'Unknown Title')} ({doc.get('year', 'Unknown Year')}) "
            f"by {', '.join(doc.get('authors', [])) if doc.get('authors') else 'Unknown Authors'}"
            for doc in retrieved_docs[:5]  # 只取前5个匹配项
        ]) if retrieved_docs else "未检索到相关文献"
        
        return prompt_template.format(
            user_reference=reference,
            retrieved_docs=retrieved_text
        )
    
    def _parse_fact_check_result(self, analysis: str, reference: str) -> ReferenceCheckResult:
        """解析LLM的文献真实性检查结果"""
        # 尝试解析JSON响应
        import json
        import re
        
        json_match = re.search(r'\{.*\}', analysis, re.DOTALL)
        if json_match:
            try:
                data = json.loads(json_match.group())
                
                issues = []
                if 'issues' in data:
                    for issue_data in data['issues']:
                        issue = Issue()
                        issue.code = issue_data.get('type', 'UNKNOWN')
                        issue.message = issue_data.get('description', '')
                        
                        severity_map = {
                            'HIGH': Severity.HIGH,
                            'MEDIUM': Severity.MEDIUM,
                            'LOW': Severity.LOW
                        }
                        issue.severity = severity_map.get(issue_data.get('severity', 'MEDIUM'), Severity.MEDIUM)
                        
                        issues.append(issue)
                
                return ReferenceCheckResult(
                    is_valid=data.get('is_valid', False),
                    issues=issues,
                    confidence_score=data.get('confidence_score', 0.0),
                    details={"explanation": data.get('explanation', '')}
                )
            except json.JSONDecodeError:
                pass
        
        # 如果JSON解析失败，返回默认结果
        issue = Issue()
        issue.code = "PARSING_ERROR"
        issue.message = "无法解析文献真实性检查结果"
        issue.severity = Severity.LOW
        
        return ReferenceCheckResult(
            is_valid=False,
            issues=[issue],
            confidence_score=0.0,
            details={"raw_analysis": analysis}
        )
    
    def _simple_analysis(self, reference: str, extracted_info: Dict[str, str]) -> ReferenceCheckResult:
        """简单的文献分析（当LLM不可用时）"""
        # 检查参考文献格式的基本完整性
        issues = []
        
        if not extracted_info.get('title'):
            issue = Issue()
            issue.code = "MISSING_TITLE"
            issue.message = "参考文献缺少标题信息"
            issue.severity = Severity.MEDIUM
            issues.append(issue)
        
        if not extracted_info.get('year'):
            issue = Issue()
            issue.code = "MISSING_YEAR"
            issue.message = "参考文献缺少年份信息"
            issue.severity = Severity.LOW
            issues.append(issue)
        
        if not extracted_info.get('authors'):
            issue = Issue()
            issue.code = "MISSING_AUTHORS"
            issue.message = "参考文献缺少作者信息"
            issue.severity = Severity.MEDIUM
            issues.append(issue)
        
        # 默认假设文献格式有效但真实性未知
        return ReferenceCheckResult(
            is_valid=len(issues) == 0,
            issues=issues,
            confidence_score=0.5 if len(issues) == 0 else 0.2,
            details={"note": "由于缺少LLM支持，仅进行了格式检查"}
        )