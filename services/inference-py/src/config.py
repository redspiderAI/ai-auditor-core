"""配置管理模块，使用Pydantic Settings管理应用配置"""
from pydantic_settings import BaseSettings
from pydantic import ConfigDict
from typing import Optional


class Settings(BaseSettings):
    """应用配置设置"""

    # API配置
    dashscope_api_key: str

    # 服务器配置
    host: str = "127.0.0.1"
    port: int = 50051

    # Milvus配置
    milvus_host: str = "localhost"
    milvus_port: str = "19530"
    milvus_user: Optional[str] = None
    milvus_password: Optional[str] = None

    # 模型配置
    model_name: str = "qwen-max"
    window_size: int = 3

    # 调试配置
    debug: bool = False

    model_config = ConfigDict(
        env_file=".env",
        env_file_encoding='utf-8'
    )


# 创建全局配置实例
settings = Settings()