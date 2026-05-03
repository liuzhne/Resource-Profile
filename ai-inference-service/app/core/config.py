import os
from functools import lru_cache


class Settings:
    # FastAPI
    APP_NAME: str = "ai-inference-service"
    APP_VERSION: str = "1.0.0"
    PORT: int = int(os.environ.get("PORT", "8090"))

    # LLM (llama.cpp OpenAI-compatible)
    LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "http://host.docker.internal:8091/v1")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "qwen2.5-14b-instruct-q5_k_m")
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "dummy")
    LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0.3"))
    LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "2048"))
    LLM_TIMEOUT: int = int(os.getenv("LLM_TIMEOUT", "60"))

    # Milvus
    MILVUS_HOST: str = os.getenv("MILVUS_HOST", "milvus-standalone")
    MILVUS_PORT: str = os.getenv("MILVUS_PORT", "19530")
    MILVUS_COLLECTIONS: dict = {
        "case": os.getenv("MILVUS_COLLECTION_CASE", "edu_cases"),
        "psychology": os.getenv("MILVUS_COLLECTION_PSY", "edu_psychology"),
        "policy": os.getenv("MILVUS_COLLECTION_POLICY", "edu_policies"),
        "success": os.getenv("MILVUS_COLLECTION_SUCCESS", "edu_success"),
    }

    # Embedding —— 宿主机 llama.cpp BGE-large-zh-v1.5（Metal GPU 加速，端口 8092）
    EMBEDDING_BASE_URL: str = os.getenv("EMBEDDING_BASE_URL", "http://host.docker.internal:8092/v1")
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "bge-large-zh-v1.5")
    EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "1024"))

    # Reranker —— 宿主机 llama.cpp BGE-reranker-base（端口 8093，启动时带 --reranking）
    RERANKER_BASE_URL: str = os.getenv("RERANKER_BASE_URL", "http://host.docker.internal:8093")
    RERANKER_MODEL: str = os.getenv("RERANKER_MODEL", "bge-reranker-base")
    RERANKER_ENABLED: bool = os.getenv("RERANKER_ENABLED", "true").lower() in ("1", "true", "yes")

    # RAG
    RAG_TOP_K: int = int(os.getenv("RAG_TOP_K", "5"))
    RAG_RECALL_EXPAND: int = int(os.getenv("RAG_RECALL_EXPAND", "3"))  # 候选 = top_k * expand


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
