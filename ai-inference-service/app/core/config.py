import os
from functools import lru_cache

from azure.core.settings import Settings


class Settings:
     # FastAPI
     APP_NAME: str = "ai-inference-service"
     APP_VERSION: str = "1.0.0"
     PORT: int = int(os.environ.get("PORT", "8090"))

     # LLM
     LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "http://host.docker.internal:8090/v1")
     LLM_MODEL: str = os.getenv("LLM_MODEL", "qwen2.5-32b-instruct-q4_k_m")
     LLM_API_KEY: str = os.getenv("LLM_API_KEY", "dummy")
     LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0.3"))
     LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "2048"))

     # Milvus
     MILVUS_HOST: str = os.getenv("MILVUS_HOST", "milvus-standalone")
     MILVUS_PORT: str = os.getenv("MILVUS_PORT", "19530")

     # Embedding
     EMBEDDING_BASE_URL: str = os.getenv("EMBEDDING_BASE_URL", "http://host.docker.internal:8091/v1")
     EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "qwen2.5-14b")

@lru_cache()
def get_settings() -> Settings:
     return Settings()

settings = get_settings()