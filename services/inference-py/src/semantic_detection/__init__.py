"""Semantic detection module for academic text analysis."""

from .comprehensive_detector import ComprehensiveSemanticDetector
from .semantic_detector import SemanticDetector
from .llm_detector import LLMDetector

__all__ = [
    "ComprehensiveSemanticDetector",
    "SemanticDetector",
    "LLMDetector"
]
