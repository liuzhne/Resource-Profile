"""
Milvus 单例客户端 — 提供集合连接 + 向量搜索能力。
集合未初始化或连接失败时，搜索方法返回空列表，链路降级而非阻断。
"""
import logging
from typing import Any, Dict, List, Optional

from pymilvus import Collection, connections, utility

from app.core.config import settings

logger = logging.getLogger(__name__)

_CONN_ALIAS = "default"
_connected = False


def _ensure_connected() -> bool:
    global _connected
    if _connected:
        return True
    try:
        connections.connect(
            alias=_CONN_ALIAS,
            host=settings.MILVUS_HOST,
            port=settings.MILVUS_PORT,
            timeout=5,
        )
        _connected = True
        logger.info("Milvus 已连接 %s:%s", settings.MILVUS_HOST, settings.MILVUS_PORT)
        return True
    except Exception as exc:
        logger.error("Milvus 连接失败: %s", exc)
        return False


def search(
    collection_name: str,
    query_vector: List[float],
    top_k: int = 5,
    expr: Optional[str] = None,
    output_fields: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """向指定集合执行向量搜索；若集合不存在或连接失败，返回空列表。"""
    if not _ensure_connected():
        return []

    try:
        if not utility.has_collection(collection_name, using=_CONN_ALIAS):
            logger.warning("Milvus 集合 %s 不存在，返回空结果", collection_name)
            return []

        collection = Collection(collection_name, using=_CONN_ALIAS)
        collection.load()

        results = collection.search(
            data=[query_vector],
            anns_field="embedding",
            param={"metric_type": "IP", "params": {"nprobe": 16}},
            limit=top_k,
            expr=expr,
            output_fields=output_fields or ["chunk_id", "title", "content", "source"],
        )

        hits: List[Dict[str, Any]] = []
        for hit in results[0]:
            payload = {"score": float(hit.distance)}
            for field in output_fields or ["chunk_id", "title", "content", "source"]:
                try:
                    payload[field] = hit.entity.get(field)
                except Exception:
                    payload[field] = None
            hits.append(payload)
        return hits

    except Exception as exc:
        logger.error("Milvus 搜索失败 collection=%s: %s", collection_name, exc)
        return []
