"""参考文献真实性校验模块
实现文献检索与真伪比对功能（RAG）
"""

import os
import re
import logging
import traceback
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

try:
    from sentence_transformers import SentenceTransformer

    SENTENCE_TRANSFORMERS_AVAILABLE = True
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False

from ..protos.auditor_pb2 import Issue, Severity
from ..config import settings
from .milvus_lite import (
    EMBEDDING_DIM,
    MILVUS_COLLECTION_NAME,
    ensure_collection,
    start_milvus_lite,
)


@dataclass
class ReferenceCheckResult:
    """参考文献检查结果"""

    is_valid: bool
    issues: List[Issue]
    confidence_score: float
    details: Dict[str, Any]


class ReferenceFactChecker:
    """参考文献真实性校验器"""

    def __init__(
        self, api_key: Optional[str] = None, milvus_config: Optional[Dict] = None
    ):
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
            "port": settings.milvus_port,
        }

        self._collection_name = MILVUS_COLLECTION_NAME
        self._embedding_dim = EMBEDDING_DIM
        self._milvus_uri: Optional[str] = None
        self._logger = logging.getLogger(__name__)

        if MILVUS_AVAILABLE:
            self.milvus_connected = self._init_milvus()
        else:
            self.milvus_connected = False

        # 延迟加载嵌入模型，减少启动开销
        self._embedder = None
        self._embedder_name = "all-MiniLM-L6-v2"

    def _milvus_uri_from_config(self) -> str:
        host = self.milvus_config.get("host", "127.0.0.1")
        port = str(self.milvus_config.get("port", "19530"))
        return f"http://{host}:{port}"

    def _init_milvus(self) -> bool:
        """Try configured Milvus first, then fall back to embedded Milvus Lite."""
        if os.environ.get("MILVUS_LITE_DISABLED"):
            return False
        try:
            connections.connect(**self.milvus_config)
            uri = self._milvus_uri_from_config()
            ensure_collection(uri, collection_name=self._collection_name, dim=self._embedding_dim)
            self._milvus_uri = uri
            return True
        except Exception as e:
            print(f"Milvus连接失败，将尝试启动内置Milvus Lite: {e}")

        try:
            uri = start_milvus_lite()
            ensure_collection(uri, collection_name=self._collection_name, dim=self._embedding_dim)
            self._milvus_uri = uri
            return True
        except Exception as e:
            print(f"内置Milvus Lite启动失败: {e}")
            return False

    async def check_references(
        self, references: List[str]
    ) -> List[ReferenceCheckResult]:
        """
        检查参考文献的真实性

        Args:
            references: 参考文献列表

        Returns:
            List[ReferenceCheckResult]: 检查结果列表
        """
        results = []

        for reference in references or []:
            # 跳过 None / 空字符串，避免无意义高危错误
            if reference is None:
                continue
            if isinstance(reference, str) and not reference.strip():
                continue

            try:
                result = await self._check_single_reference(reference)
            except Exception as e:  # 防御性兜底，避免单条参考导致整体中断
                tb = traceback.format_exc()
                self._logger.error(
                    "Reference fact check failed with unhandled exception",
                    extra={
                        "reference_preview": str(reference)[:200] if reference is not None else None,
                        "reference_type": str(type(reference)),
                        "error": str(e),
                        "traceback": tb,
                    },
                )

                issue = Issue()
                issue.code = "FACT_CHECK_ERROR"
                issue.message = f"文献真实性检查出错: {str(e)}"
                issue.severity = Severity.HIGH

                result = ReferenceCheckResult(
                    is_valid=False,
                    issues=[issue],
                    confidence_score=0.0,
                    details={
                        "error": str(e),
                        "reference": reference,
                        "traceback": tb,
                    },
                )

            results.append(result)

        return results

    async def _check_single_reference(self, reference: str) -> ReferenceCheckResult:
        """检查单个参考文献"""
        if reference is None:
            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = "文献真实性检查出错: 输入为空"
            issue.severity = Severity.HIGH
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"error": "reference is None"},
            )

        if not isinstance(reference, str):
            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = "文献真实性检查出错: 输入类型无效"
            issue.severity = Severity.HIGH
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"error": f"invalid type: {type(reference)}"},
            )

        reference_text = reference.strip()
        if not reference_text:
            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = "文献真实性检查出错: 输入为空字符串"
            issue.severity = Severity.HIGH
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"error": "reference is empty string"},
            )

        # 提取参考文献的关键信息
        try:
            extracted_info = self._extract_reference_info(reference_text)
        except Exception as e:
            tb = traceback.format_exc()
            self._logger.error(
                "Extract reference info failed",
                extra={
                    "reference_preview": reference_text[:200],
                    "error": str(e),
                    "traceback": tb,
                },
            )

            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = f"文献真实性检查出错: {str(e)}"
            issue.severity = Severity.HIGH
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"error": str(e), "traceback": tb, "reference": reference_text},
            )

        # 如果Milvus可用，进行向量检索
        if self.milvus_connected:
            retrieved_docs = await self._retrieve_similar_documents(extracted_info)
        else:
            retrieved_docs = []

        # 使用LLM进行比对分析
        if DASHSCOPE_AVAILABLE and self.api_key:
            analysis_result = await self._analyze_with_llm(
                reference_text, retrieved_docs
            )
        else:
            # 如果没有LLM，仅基于提取的信息进行简单检查
            analysis_result = self._simple_analysis(reference_text, extracted_info)

        return analysis_result

    def _extract_reference_info(self, reference: str) -> Dict[str, str]:
        """从参考文献中提取关键信息"""
        info = {}
        reference = (reference or "").strip()

        # 移除形如 [1] 的编号前缀并规范空白，降低解析干扰
        reference = re.sub(r"^\[\d+\]\s*", "", reference)
        reference = re.sub(r"\s+", " ", reference)

        # 提取标题（通常在引号或书名号内，也包括句点后的标题）
        title_match = re.search(r'[""''"'']([^""''"'']+)[""''"'']|\[([^]]+)\]|《([^》]+)》', reference)
        if title_match:
            info["title"] = (
                title_match.group(1) or title_match.group(2) or title_match.group(3)
            )
        else:
            # 中文学位/期刊文献常见格式: 作者. 标题[类型]. 地点:单位,年份.
            # 抽取作者后第一个句号/顿号/冒号之后、下一个分隔符前的片段作为标题
            title_pattern_cn = (
                r"^[^.。]+[\.。]\s*([^\.。\[]+?)\s*(?:\[|\(|（|\.|。|:|：)"
            )
            title_match = re.search(title_pattern_cn, reference)
            if title_match:
                info["title"] = title_match.group(1).strip()
            else:
                # 英文格式: (2023). Title. Journal...
                title_pattern_en = r"\(\d{4}\)\.\s*([^.]+)"
                title_match = re.search(title_pattern_en, reference)
                if title_match:
                    title = title_match.group(1).strip()
                    if not re.search(r"Journal|Conference", title):
                        info["title"] = title

        # 提取年份
        year_match = re.search(r"(?:\b\d{4}\b|出版时间[:：]\s*(\d{4}))", reference)
        if year_match:
            info["year"] = year_match.group(0).strip()
        else:
            # 尝试从 DOI 邻近获取年份（常见 20xx 出现一次）
            alt_year = re.search(r"20\d{2}", reference)
            if alt_year:
                info["year"] = alt_year.group(0)

        # 提取期刊/会议名
        journal_match = re.search(
            r'in\s+([A-Za-z\s]+(?:[A-Z][a-z]*\s*)+)|发表在\s*([^(《"「]+)', reference
        )
        if journal_match:
            info["journal"] = (journal_match.group(1) or journal_match.group(2)).strip()

        # 提取作者
        author_match = re.search(r"^([^. ,;]+)", reference)
        if author_match:
            authors = author_match.group(1).strip()
            # 简单过滤掉数字和特殊字符开头的部分
            if not re.match(r"^[\d\s\(\)]+", authors):
                info["authors"] = authors

        return info

    async def _retrieve_similar_documents(
        self, reference_info: Dict[str, str]
    ) -> List[Dict[str, Any]]:
        """从Milvus中检索相似文档"""
        if not MILVUS_AVAILABLE or not self.milvus_connected:
            return []

        # 这里只是一个示例实现，实际应用中需要根据具体的Milvus集合结构来调整
        try:
            # 假设有一个名为"academic_papers"的集合
            collection_name = self._collection_name

            # 检查集合是否存在
            from pymilvus import utility
            if not utility.has_collection(collection_name):
                return []

            from pymilvus import Collection

            # 选择检索文本：标题优先，其次作者+年份
            query_text = reference_info.get("title") or " ".join(
                filter(
                    None,
                    [
                        reference_info.get("authors"),
                        reference_info.get("year"),
                        reference_info.get("journal"),
                    ],
                )
            )
            if not query_text:
                return []

            query_vector = self._build_query_vector(query_text)
            if query_vector is None:
                return []

            collection = Collection(collection_name)
            try:
                collection.load()
            except Exception:
                pass

            search_params = {"metric_type": "IP", "params": {"nprobe": 10}}
            try:
                search_res = collection.search(
                    data=[query_vector],
                    anns_field="embedding",
                    param=search_params,
                    limit=5,
                    output_fields=["title", "authors", "year", "journal", "reference"],
                )
            except Exception as e:
                print(f"Milvus检索出错: {e}")
                return []

            docs: List[Dict[str, Any]] = []
            for hit in search_res[0]:
                fields = getattr(hit, "entity", None) or {}
                docs.append(
                    {
                        "title": fields.get("title"),
                        "authors": fields.get("authors"),
                        "year": fields.get("year"),
                        "journal": fields.get("journal"),
                        "reference": fields.get("reference"),
                        "score": float(hit.score) if hasattr(hit, "score") else None,
                    }
                )

            return docs
        except Exception as e:
            print(f"Milvus检索出错: {e}")
            return []

    def _build_query_vector(self, text: str) -> Optional[List[float]]:
        """构建查询向量。返回 None 表示无法生成。"""
        if not SENTENCE_TRANSFORMERS_AVAILABLE:
            print("sentence-transformers 未安装，无法进行向量检索")
            return None

        if self._embedder is None:
            try:
                self._embedder = SentenceTransformer(self._embedder_name)
            except Exception as e:
                print(f"加载嵌入模型失败: {e}")
                return None

        try:
            vec = self._embedder.encode([text], normalize_embeddings=True)
            return vec[0].tolist()
        except Exception as e:
            print(f"生成嵌入失败: {e}")
            return None

    async def _analyze_with_llm(
        self, reference: str, retrieved_docs: List[Dict[str, Any]]
    ) -> ReferenceCheckResult:
        """使用LLM分析参考文献真实性"""
        if not DASHSCOPE_AVAILABLE or not self.api_key:
            return self._simple_analysis(
                reference, self._extract_reference_info(reference)
            )

        prompt = self._build_fact_check_prompt(reference, retrieved_docs)

        try:
            response = dashscope.Generation.call(
                model="qwen-long",  # 使用支持长文本的模型
                prompt=prompt,
                top_p=0.8,
                temperature=0.5,
                max_tokens=1000,
            )

            if response.status_code == 200:
                analysis = getattr(response, "output", None)
                analysis_text = getattr(analysis, "text", None) if analysis else None
                if not analysis_text:
                    # 无返回文本，降级为简易检查
                    fallback = self._simple_analysis(
                        reference, self._extract_reference_info(reference)
                    )
                    fallback.details["error"] = "LLM返回为空"
                    return fallback
                return self._parse_fact_check_result(analysis_text, reference)
            else:
                # API调用失败，降级为简易检查
                fallback = self._simple_analysis(
                    reference, self._extract_reference_info(reference)
                )
                fallback.details["error"] = f"API Error: {response}"
                return fallback
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
                details={"error": str(e)},
            )

    def _build_fact_check_prompt(
        self, reference: str, retrieved_docs: List[Dict[str, Any]]
    ) -> str:
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

        retrieved_text = (
            "\n".join(
                [
                    f"- {doc.get('title', 'Unknown Title')} ({doc.get('year', 'Unknown Year')}) "
                    f"by {', '.join(doc.get('authors', [])) if doc.get('authors') else 'Unknown Authors'}"
                    for doc in retrieved_docs[:5]  # 只取前5个匹配项
                ]
            )
            if retrieved_docs
            else "未检索到相关文献"
        )

        return prompt_template.format(
            user_reference=reference, retrieved_docs=retrieved_text
        )

    def _parse_fact_check_result(
        self, analysis: str, reference: str
    ) -> ReferenceCheckResult:
        """解析LLM的文献真实性检查结果"""
        if analysis is None:
            issue = Issue()
            issue.code = "FACT_CHECK_ERROR"
            issue.message = "文献真实性检查出错: 未获取到分析结果"
            issue.severity = Severity.HIGH
            return ReferenceCheckResult(
                is_valid=False,
                issues=[issue],
                confidence_score=0.0,
                details={"raw_analysis": None},
            )

        if not isinstance(analysis, str):
            analysis = str(analysis)

        # 尝试解析JSON响应
        import json
        import re

        json_match = re.search(r"\{.*\}", analysis, re.DOTALL)
        if json_match:
            try:
                data = json.loads(json_match.group())

                issues = []
                if "issues" in data:
                    for issue_data in data["issues"]:
                        issue = Issue()
                        issue.code = issue_data.get("type", "UNKNOWN")
                        issue.message = issue_data.get("description", "")

                        severity_map = {
                            "HIGH": Severity.HIGH,
                            "MEDIUM": Severity.MEDIUM,
                            "LOW": Severity.LOW,
                        }
                        issue.severity = severity_map.get(
                            issue_data.get("severity", "MEDIUM"), Severity.MEDIUM
                        )

                        issues.append(issue)

                return ReferenceCheckResult(
                    is_valid=data.get("is_valid", False),
                    issues=issues,
                    confidence_score=data.get("confidence_score", 0.0),
                    details={"explanation": data.get("explanation", "")},
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
            details={"raw_analysis": analysis},
        )

    def _simple_analysis(
        self, reference: str, extracted_info: Dict[str, str]
    ) -> ReferenceCheckResult:
        """简单的文献分析（当LLM不可用时）"""
        # 检查参考文献格式的基本完整性
        issues = []

        if not extracted_info.get("title"):
            issue = Issue()
            issue.code = "MISSING_TITLE"
            issue.message = "参考文献缺少标题信息"
            issue.severity = Severity.MEDIUM
            issues.append(issue)

        if not extracted_info.get("year"):
            issue = Issue()
            issue.code = "MISSING_YEAR"
            issue.message = "参考文献缺少年份信息"
            issue.severity = Severity.LOW
            issues.append(issue)

        if not extracted_info.get("authors"):
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
            details={"note": "由于缺少LLM支持，仅进行了格式检查"},
        )
