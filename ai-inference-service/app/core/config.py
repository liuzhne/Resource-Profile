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
    # I-1.3：hybrid（dense + BM25 + RRF）灰度开关。默认关 → 纯 dense（与上线前行为一致）。
    # 开启后在 dense 召回候选池上做 BM25 重排并 RRF 融合，对专有名词 query 更鲁棒。
    RAG_HYBRID_ENABLED: bool = os.getenv("RAG_HYBRID_ENABLED", "false").lower() in ("1", "true", "yes")

    # Redis 缓存（与 Java 服务共用同一实例；连接失败时调用方走无缓存路径）
    REDIS_HOST: str = os.getenv("REDIS_HOST", "localhost")
    REDIS_PORT: int = int(os.getenv("REDIS_PORT", "6379"))
    REDIS_PASSWORD: str = os.getenv("REDIS_PASSWORD", "")
    REDIS_DB: int = int(os.getenv("REDIS_DB", "0"))
    EMBEDDING_CACHE_TTL: int = int(os.getenv("EMBEDDING_CACHE_TTL", "86400"))  # 24h
    RAG_CACHE_TTL: int = int(os.getenv("RAG_CACHE_TTL", "3600"))  # 1h

    # G-4.3：管理员接口（/api/v1/rag/upsert 等）的预共享 token。
    # 默认空 → 端点 503（运维必须显式配置才能启用，安全 fail-closed）
    ADMIN_TOKEN: str = os.getenv("EDUCARE_ADMIN_TOKEN", "")
    UPSERT_HASH_TTL: int = int(os.getenv("EDUCARE_UPSERT_HASH_TTL", str(7 * 24 * 3600)))  # 7d

    # G-5.2：Langfuse trace。空 keys → SDK no-op，不影响主流程。
    LANGFUSE_PUBLIC_KEY: str = os.getenv("LANGFUSE_PUBLIC_KEY", "")
    LANGFUSE_SECRET_KEY: str = os.getenv("LANGFUSE_SECRET_KEY", "")
    LANGFUSE_HOST: str = os.getenv("LANGFUSE_HOST", "http://localhost:3001")

    # H-1.3：knowledge-rag MCP server（FastMCP，独立进程）。
    # 端口与 mcp-student-data(8094) 配对，主 FastAPI 仍占 8090。
    # transport 固定为 Streamable HTTP，单端点 path（spec 2025-03-26 推荐）。
    MCP_KNOWLEDGE_RAG_PORT: int = int(os.getenv("MCP_KNOWLEDGE_RAG_PORT", "8095"))
    MCP_KNOWLEDGE_RAG_PATH: str = os.getenv("MCP_KNOWLEDGE_RAG_PATH", "/mcp")

    # H-5：memory-server MCP（FastMCP，独立进程，端口 8096，与 knowledge-rag(8095) 对称）。
    # 暴露 recall_student_history / save_episode / summarize_long_term 三个工具。
    MCP_MEMORY_PORT: int = int(os.getenv("MCP_MEMORY_PORT", "8096"))
    MCP_MEMORY_PATH: str = os.getenv("MCP_MEMORY_PATH", "/mcp")
    # 记忆分层 TTL / 容量（Redis 持久化；Semantic 未来可升级到 Milvus 向量召回，见 MEMORY_DESIGN.md）
    MEMORY_WORKING_TTL: int = int(os.getenv("EDUCARE_MEMORY_WORKING_TTL", str(3600)))            # Working 1h
    MEMORY_EPISODE_TTL: int = int(os.getenv("EDUCARE_MEMORY_EPISODE_TTL", str(90 * 24 * 3600)))  # Episodic 90d
    MEMORY_SEMANTIC_TTL: int = int(os.getenv("EDUCARE_MEMORY_SEMANTIC_TTL", str(180 * 24 * 3600)))  # Semantic 180d
    MEMORY_EPISODE_MAX: int = int(os.getenv("EDUCARE_MEMORY_EPISODE_MAX", "50"))                 # 每生最多保留 N 条 episodic


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
