"""使用LangGraph实现的状态机和滑动窗口逻辑
用于处理长文本并维护全局状态以确保术语一致性
"""
import re
from typing import Dict, List, Any, TypeAlias
from typing_extensions import TypedDict
from langgraph.graph import StateGraph
from .semantic_detection import ComprehensiveSemanticDetector
from .logic_consistency import LogicConsistencyChecker
from .config import settings
from .protos import auditor_pb2
IssueType: TypeAlias = auditor_pb2.Issue



class GraphState(TypedDict):
    """图状态定义"""
    sections: List[Dict[str, Any]]
    current_index: int
    total_sections: int
    detected_issues: List[IssueType]
    global_terms: Dict[str, List[int]]  # 术语及其出现的章节
    window_size: int
    processed_windows: int
    summary_section: str
    conclusion_section: str
    consistency_issues: List[IssueType]


class SlidingWindowProcessor:
    """滑动窗口处理器"""
    
    def __init__(self, api_key: str = None):
        """
        初始化滑动窗口处理器

        Args:
            api_key: 通义千问API密钥
        """
        # 优先使用传入的API密钥，否则使用配置文件中的
        effective_api_key = api_key or settings.dashscope_api_key
        self.semantic_detector = ComprehensiveSemanticDetector(api_key=effective_api_key)
        self.consistency_checker = LogicConsistencyChecker(api_key=effective_api_key)
        self.api_key = effective_api_key

        # 构建状态图
        self.workflow = self._build_workflow()
    
    def _build_workflow(self) -> StateGraph:
        """构建LangGraph工作流"""
        workflow = StateGraph(GraphState)
        
        # 添加节点
        workflow.add_node("process_window", self._process_window)
        workflow.add_node("update_global_state", self._update_global_state)
        workflow.add_node("check_consistency", self._check_consistency)
        
        # 设置入口点
        workflow.set_entry_point("process_window")
        
        # 添加边
        workflow.add_edge("process_window", "update_global_state")
        workflow.add_edge("update_global_state", "check_consistency")

        # 条件边：如果还有更多窗口需要处理，则循环回去
        workflow.add_conditional_edges(
            "check_consistency",
            self._continue_condition,
            {
                "continue": "process_window",
                "end": "__end__"
            }
        )

        return workflow.compile()
    
    def _process_window(self, state: GraphState) -> Dict[str, Any]:
        """处理当前窗口的文本"""
        current_idx = state["current_index"]
        window_size = state["window_size"]
        
        # 获取当前窗口的章节
        end_idx = min(current_idx + window_size, state["total_sections"])
        current_sections = state["sections"][current_idx:end_idx]
        
        issues = []
        
        # 对当前窗口中的每个章节进行语义检测
        for section in current_sections:
            text = section.get("text", "")
            section_id = section.get("section_id", current_idx)
            
            # 使用语义检测器检测问题
            section_issues = self.semantic_detector.detect_issues(text)
            
            # 更新section_id到当前实际章节ID
            for issue in section_issues:
                issue.section_id = section_id
            
            issues.extend(section_issues)
        
        return {
            "detected_issues": issues,
            "current_index": end_idx
        }
    
    def _update_global_state(self, state: GraphState) -> Dict[str, Any]:
        """更新全局状态，包括术语跟踪"""
        updated_terms = state.get("global_terms", {})

        # 在当前窗口的章节中提取术语（缩写、大写词、含“算法/模型/方法/网络/系统”的短语）
        term_patterns = [
            r"\b[A-Z]{2,5}\b",
            r"[\u4e00-\u9fff]{2,6}(算法|模型|方法|网络|系统)",
            r"\b\w+(?:algorithm|model|method|network|system)\b",
        ]

        current_idx = state["current_index"]
        window_size = state["window_size"]
        end_idx = min(current_idx, state["total_sections"])
        start_idx = max(0, end_idx - window_size)
        window_sections = state["sections"][start_idx:end_idx]

        for section in window_sections:
            sec_id = section.get("section_id", 0)
            text = section.get("text", "")
            for pattern in term_patterns:
                for match in re.finditer(pattern, text, flags=re.IGNORECASE):
                    term = match.group()
                    updated_terms.setdefault(term, [])
                    if sec_id not in updated_terms[term]:
                        updated_terms[term].append(sec_id)

        return {
            "global_terms": updated_terms,
            "processed_windows": state["processed_windows"] + 1
        }
    
    def _check_consistency(self, state: GraphState) -> Dict[str, Any]:
        """检查一致性"""
        # 检查摘要-结论一致性
        summary_section = state.get("summary_section", "")
        conclusion_section = state.get("conclusion_section", "")
        
        # 如果当前处理的是摘要或结论部分，保存它们
        for section in state["sections"]:
            sec_type = section.get("type", "").lower()
            sec_text = section.get("text", "")
            
            if 'summary' in sec_type or 'abstract' in sec_type:
                summary_section = sec_text
            elif 'conclusion' in sec_type or 'conclude' in sec_type:
                conclusion_section = sec_text
        
        return {
            "summary_section": summary_section,
            "conclusion_section": conclusion_section
        }
    
    def _continue_condition(self, state: GraphState) -> str:
        """决定是否继续处理下一个窗口"""
        if state["current_index"] < state["total_sections"]:
            return "continue"
        else:
            return "end"
    
    async def process_document(self, sections: List[Dict[str, Any]], window_size: int = 3) -> GraphState:
        """
        处理整个文档
        
        Args:
            sections: 文档章节列表
            window_size: 滑动窗口大小
            
        Returns:
            GraphState: 处理完成后的最终状态
        """
        initial_state = GraphState(
            sections=sections,
            current_index=0,
            total_sections=len(sections),
            detected_issues=[],
            global_terms={},
            window_size=window_size,
            processed_windows=0,
            summary_section="",
            conclusion_section="",
            consistency_issues=[],
        )
        
        # 执行工作流
        final_state = await self.workflow.ainvoke(initial_state)

        # 追加长文本一致性检查（摘要-结论、术语一致性等）
        consistency_result = await self.consistency_checker.check_consistency(sections)
        final_state["consistency_issues"] = consistency_result.issues
        # 把术语使用情况并入全局状态
        final_state["global_terms"].update(consistency_result.term_usage)
        
        return final_state


# 示例用法函数
async def run_sliding_window_analysis(sections: List[Dict[str, Any]], api_key: str = None) -> List[IssueType]:
    """
    运行滑动窗口分析的便捷函数
    
    Args:
        sections: 文档章节列表
        api_key: 通义千问API密钥
        
    Returns:
        List[IssueType]: 检测到的所有问题
    """
    processor = SlidingWindowProcessor(api_key=api_key)
    final_state = await processor.process_document(sections)
    # 汇总窗口内检测 + 一致性检测的全部问题
    all_issues = list(final_state["detected_issues"])
    all_issues.extend(final_state.get("consistency_issues", []))
    return all_issues