"""
Reranker 客户端 — Cohere /rerank 兼容协议（Infinity / Voyage / Cohere 均可对接）。

请求体:
    {"model": "...", "query": "...", "documents": ["...", ...], "top_n": 5}
响应体:
    {"results": [{"index": 0, "relevance_score": 0.97}, ...]}

失败时返回保持原顺序的占位结果，确保 RAG 链路不被中断。
"""
import logging
from typing import List, Dict

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.core.config import settings

logger = logging.getLogger(__name__)


class RerankerClient:
    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.RERANKER_BASE_URL,
            timeout=30.0,
        )

    @retry(stop=stop_after_attempt(2), wait=wait_exponential(min=1, max=4))
    async def rerank(
        self,
        query: str,
        documents: List[str],
        top_n: int | None = None,
    ) -> List[Dict[str, float]]:
        if not documents:
            return []
        try:
            payload: Dict = {
                "model": settings.RERANKER_MODEL,
                "query": query,
                "documents": documents,
            }
            if top_n is not None:
                payload["top_n"] = top_n
            resp = await self._client.post("/rerank", json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("results", [])
        except Exception as exc:
            logger.error("Rerank 失败: %s", exc)
            return [
                {"index": i, "relevance_score": 0.0} for i in range(len(documents))
            ]

    async def close(self) -> None:
        await self._client.aclose()


_reranker_client: RerankerClient | None = None


def get_reranker_client() -> RerankerClient:
    global _reranker_client
    if _reranker_client is None:
        _reranker_client = RerankerClient()
    return _reranker_client
