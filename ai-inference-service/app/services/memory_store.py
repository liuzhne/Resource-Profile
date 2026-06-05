"""H-5.2：学生记忆分层存储（Redis 持久化）。

四层记忆（参考 Mem0/认知记忆模型，本项目自实现避免引入不稳定外部 SDK，决策见 MEMORY_DESIGN.md）：

- **Working**（工作记忆）：单次会话临时上下文，短 TTL。key=edu:mem:working:{sid}（string）
- **Episodic**（情景记忆）：离散事件流（一次预警/一次谈心/一次方案），有序、有界。
  key=edu:mem:episode:{sid}（list，LPUSH 新事件 + LTRIM 到 MEMORY_EPISODE_MAX）
- **Semantic**（语义记忆）：从情景蒸馏出的稳定事实/画像摘要。key=edu:mem:semantic:{sid}（string JSON）
- **Procedural**（程序记忆）：有效干预策略/做法，作为 semantic 摘要里的 strategies 字段一并存储

所有写/读在 Redis 不可用时降级（返回 False / 空），不抛异常，与项目既有 redis_client 一致。
"""
import json
import logging
import time
from typing import Any, Dict, List, Optional

from app.core.config import settings
from app.services.redis_client import get_redis

logger = logging.getLogger(__name__)

_PREFIX = "edu:mem"


# -------- 纯函数（无 IO，便于单测） --------

def working_key(student_id: str) -> str:
    return f"{_PREFIX}:working:{student_id}"


def episode_key(student_id: str) -> str:
    return f"{_PREFIX}:episode:{student_id}"


def semantic_key(student_id: str) -> str:
    return f"{_PREFIX}:semantic:{student_id}"


def build_episode(text: str, metadata: Optional[Dict[str, Any]], ts: Optional[float] = None) -> Dict[str, Any]:
    """构造一条情景记忆。ts 缺省取当前时间。"""
    return {
        "text": (text or "").strip(),
        "metadata": metadata or {},
        "ts": ts if ts is not None else time.time(),
    }


def filter_episodes(episodes: List[Dict[str, Any]], query: str) -> List[Dict[str, Any]]:
    """按 query 子串（大小写不敏感）过滤情景；query 空则原样返回。"""
    q = (query or "").strip().lower()
    if not q:
        return episodes
    out = []
    for e in episodes:
        blob = (str(e.get("text", "")) + " " + json.dumps(e.get("metadata", {}), ensure_ascii=False)).lower()
        if q in blob:
            out.append(e)
    return out


# -------- 异步 IO（Redis 不可用时降级） --------

async def save_episode(student_id: str, text: str, metadata: Optional[Dict[str, Any]] = None) -> bool:
    """追加一条情景记忆，LTRIM 到上限，刷新 TTL。Redis 不可用返回 False。"""
    client = await get_redis()
    if client is None:
        return False
    key = episode_key(student_id)
    item = json.dumps(build_episode(text, metadata), ensure_ascii=False)
    try:
        await client.lpush(key, item)
        await client.ltrim(key, 0, settings.MEMORY_EPISODE_MAX - 1)
        await client.expire(key, settings.MEMORY_EPISODE_TTL)
        return True
    except Exception as exc:
        logger.debug("save_episode 失败 sid=%s: %s", student_id, exc)
        return False


async def list_episodes(student_id: str, limit: int = 50) -> List[Dict[str, Any]]:
    """取最近 limit 条情景（新→旧）。Redis 不可用返回空。"""
    client = await get_redis()
    if client is None:
        return []
    try:
        raw = await client.lrange(episode_key(student_id), 0, max(0, limit - 1))
    except Exception as exc:
        logger.debug("list_episodes 失败 sid=%s: %s", student_id, exc)
        return []
    out: List[Dict[str, Any]] = []
    for r in raw:
        try:
            out.append(json.loads(r))
        except Exception:
            out.append({"text": r, "metadata": {}, "ts": None})
    return out


async def get_semantic(student_id: str) -> Optional[Dict[str, Any]]:
    client = await get_redis()
    if client is None:
        return None
    try:
        raw = await client.get(semantic_key(student_id))
        return json.loads(raw) if raw else None
    except Exception as exc:
        logger.debug("get_semantic 失败 sid=%s: %s", student_id, exc)
        return None


async def set_semantic(student_id: str, summary: Dict[str, Any]) -> bool:
    client = await get_redis()
    if client is None:
        return False
    try:
        await client.setex(
            semantic_key(student_id),
            settings.MEMORY_SEMANTIC_TTL,
            json.dumps(summary, ensure_ascii=False),
        )
        return True
    except Exception as exc:
        logger.debug("set_semantic 失败 sid=%s: %s", student_id, exc)
        return False
