"""测试用例：验证各模块功能"""

import unittest

from src.semantic_detection import SemanticDetector
from src.fact_checking import ReferenceFactChecker
from src.logic_consistency import LogicConsistencyChecker
from src.sliding_window_processor import (
    SlidingWindowProcessor,
    run_sliding_window_analysis,
)
from src.protos.auditor_pb2 import Issue


class TestSemanticDetection(unittest.TestCase):
    """语义检测模块测试"""

    def setUp(self):
        self.detector = SemanticDetector()

    def test_typo_detection(self):
        """测试错别字检测"""
        text = "实验的黏度测量结果显示，份量配比较之前有所改进。"
        result = self.detector.detect_issues(text)

        # 应该检测到"份量"应该是"分量"
        typo_issues = [issue for issue in result.issues if issue.code == "TYPO"]
        self.assertGreater(len(typo_issues), 0)
        self.assertIn("份量", [issue.original_snippet for issue in typo_issues])

    # def test_punctuation_detection(self):
    #     """测试标点符号检测"""
    #     text = "这是一个测试，包含英文标点符号: comma, period. 和中文标点"
    #     result = self.detector.detect_issues(text)

    #     # 应该检测到英文标点符号
    #     punct_issues = [issue for issue in result.issues if issue.code == "PUNCTUATION"]
    #     # 由于我们的实现中只检测中文环境中的半角标点，这个测试可能不会产生结果
    #     # 但我们仍可以验证函数正常运行而不报错

    def test_colloquialism_detection(self):
        """测试口语化词汇检测"""
        text = "这个算法听说效果特别好。"
        result = self.detector.detect_issues(text)

        # 应该检测到"听说"和"特别好"
        style_issues = [issue for issue in result.issues if issue.code == "STYLE"]
        self.assertGreater(len(style_issues), 0)
        original_snippets = [issue.original_snippet for issue in style_issues]
        self.assertIn("听说", original_snippets)
        self.assertIn("特别好", original_snippets)


class TestFactChecking(unittest.IsolatedAsyncioTestCase):
    """文献真实性校验模块测试"""

    def setUp(self):
        # 使用模拟API密钥进行测试（实际上不会调用API）
        self.checker = ReferenceFactChecker(api_key="fake-key-for-test")

    async def test_reference_extraction(self):
        """测试参考文献信息提取"""
        reference = "Zhang, W., & Li, M. (2023). Advanced AI Methods in Modern Computing. Journal of Computer Science, 15(3), 45-67."
        info = self.checker._extract_reference_info(reference)

        self.assertIn("authors", info)
        self.assertIn("year", info)
        self.assertIn("title", info) or self.assertIn("journal", info)

    async def test_simple_analysis(self):
        """测试简单分析功能"""
        reference = "Sample reference for testing purposes."
        result = self.checker._simple_analysis(
            reference, self.checker._extract_reference_info(reference)
        )

        # 简单分析应该返回一些基本信息
        self.assertIsNotNone(result)
        self.assertIsInstance(result.confidence_score, float)


class TestLogicConsistency(unittest.IsolatedAsyncioTestCase):
    """逻辑一致性检查模块测试"""

    def setUp(self):
        self.checker = LogicConsistencyChecker(api_key="fake-key-for-test")

    async def test_term_consistency(self):
        """测试术语一致性检查"""
        sections = [
            {
                "section_id": 1,
                "type": "introduction",
                "text": "我们提出了卷积神经网络(CNN)方法。",
            },
            {"section_id": 2, "type": "method", "text": "CNN是一种深度学习模型。"},
            {"section_id": 3, "type": "conclusion", "text": "卷积神经网络表现优异。"},
        ]

        result = await self.checker.check_consistency(sections)

        # 检查是否正确识别了术语使用
        self.assertIsNotNone(result.term_usage)
        self.assertIsInstance(result.term_usage, dict)

    async def test_summary_conclusion_alignment(self):
        """测试摘要-结论对齐检查"""
        # 设置摘要和结论
        self.checker.summary_section = "本文提出了一种新的算法，能够提高准确率。"
        self.checker.conclusion_section = "实验表明该算法有效提高了准确率。"

        issues = await self.checker._check_summary_conclusion_alignment()

        # 由于使用假API密钥，会执行简单检查
        self.assertIsNotNone(issues)


class TestSlidingWindowProcessor(unittest.IsolatedAsyncioTestCase):
    """滑动窗口处理器测试"""

    async def test_sliding_window_processing(self):
        """测试滑动窗口处理功能"""
        sections = [
            {
                "section_id": 1,
                "type": "abstract",
                "text": "这是一篇关于AI技术的论文摘要。",
            },
            {
                "section_id": 2,
                "type": "introduction",
                "text": "介绍人工智能的发展背景。",
            },
            {"section_id": 3, "type": "method", "text": "我们使用了卷积神经网络方法。"},
            {"section_id": 4, "type": "result", "text": "实验结果表明方法有效。"},
            {"section_id": 5, "type": "conclusion", "text": "结论是该方法表现优异。"},
        ]

        # 使用假API密钥测试
        processor = SlidingWindowProcessor(api_key="fake-key-for-test")
        final_state = await processor.process_document(sections, window_size=2)

        # 验证处理结果
        self.assertIsNotNone(final_state)
        self.assertIn("detected_issues", final_state)
        self.assertIn("global_terms", final_state)
        self.assertEqual(final_state["total_sections"], len(sections))


class IntegrationTest(unittest.IsolatedAsyncioTestCase):
    """集成测试"""

    async def test_complete_pipeline(self):
        """测试完整管道"""
        sections = [
            {"section_id": 1, "type": "abstract", "text": "本文研究了人工智能技术。"},
            {
                "section_id": 2,
                "type": "introduction",
                "text": "AI技术发展迅速，但存在一些问题。",
            },
            {"section_id": 3, "type": "method", "text": "我们采用了深度学习方法。"},
            {
                "section_id": 4,
                "type": "conclusion",
                "text": "深度学习在AI领域表现优异。",
            },
        ]

        # 测试滑动窗口分析
        issues = await run_sliding_window_analysis(
            sections, api_key="fake-key-for-test"
        )

        # 验证返回的是Issue列表
        self.assertIsInstance(issues, list)
        for issue in issues:
            self.assertIsInstance(issue, Issue)


if __name__ == "__main__":
    unittest.main()
