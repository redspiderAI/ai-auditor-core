"""配置管理模块，使用Pydantic Settings管理应用配置"""
from pathlib import Path
from typing import Optional

from pydantic import ConfigDict
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置设置"""

    # API配置 - 支持DashScope和OpenAI兼容模式
    dashscope_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    openai_base_url: Optional[str] = None
    openai_model: Optional[str] = None

    # 服务器配置
    host: str = "127.0.0.1"
    port: int = 50051

    # Milvus配置
    milvus_host: str = "localhost"
    milvus_port: str = "19530"
    milvus_user: Optional[str] = None
    milvus_password: Optional[str] = None
    # 控制是否禁用内置 Milvus Lite（Windows 默认置 1 以避免嵌入式启动）
    milvus_lite_disabled: bool = True

    # 模型配置
    model_name: str = "qwen-max"
    window_size: int = 3

    # 调试配置
    debug: bool = False

    _env_path = Path(__file__).resolve().parents[1] / ".env"
    model_config = ConfigDict(env_file=str(_env_path), env_file_encoding="utf-8")

    def get_primary_api_key(self) -> str:
        """获取主要API密钥，优先使用DashScope，其次是OpenAI"""
        if self.dashscope_api_key:
            return self.dashscope_api_key
        elif self.openai_api_key:
            return self.openai_api_key
        else:
            raise ValueError("Either dashscope_api_key or openai_api_key must be provided")

    def get_model_name(self) -> str:
        """获取模型名称，优先使用OpenAI模型名称"""
        if self.openai_model:
            return self.openai_model
        return self.model_name


# 创建全局配置实例
settings = Settings()