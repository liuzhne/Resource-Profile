"""
Embedding 客户端 — 走 OpenAI 兼容 /v1/embeddings 接口。
默认指向 llama.cpp 或独立的 BGE 嵌入服务，由 EMBEDDING_BASE_URL 切换。
异常时返回零向量降级，保证 RAG 链路不被嵌入失败阻断。
"""
import logging
from typing import List

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.core.config import settings

logger = logging.getLogger(__name__)


class EmbeddingClient:
    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.EMBEDDING_BASE_URL,
            timeout=30.0,
            headers={"Authorization": f"Bearer {settings.LLM_API_KEY}"},
        )

    @retry(stop=stop_after_attempt(2), wait=wait_exponential(min=1, max=4))
    async def embed(self, text: str) -> List[float]:
        try:
            resp = await self._client.post(
                "/embeddings",
                json={"model": settings.EMBEDDING_MODEL, "input": text},
            )
            resp.raise_for_status()
            data = resp.json()
            return data["data"][0]["embedding"]
        except Exception as exc:
            logger.error("Embedding 调用失败: %s", exc)
            return [0.0] * settings.EMBEDDING_DIM

    async def close(self) -> None:
        await self._client.aclose()


_embedding_client: EmbeddingClient | None = None


def get_embedding_client() -> EmbeddingClient:
    global _embedding_client
    if _embedding_client is None:
        _embedding_client = EmbeddingClient()
    return _embedding_client
