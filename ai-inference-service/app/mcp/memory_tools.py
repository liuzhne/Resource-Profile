"""H-5.3：FastMCP memory-server 的 3 个 tool。

工具命名与 MCP_DESIGN/MEMORY_DESIGN 一致：
- recall_student_history → 召回学生语义摘要 + 最近情景
- save_episode          → 写入一条情景记忆
- summarize_long_term   → 把情景蒸馏为语义/程序记忆

底层走 app.mcp.memory_adapter（业务逻辑与装饰层分离，便于单测）。
"""
from typing import Any, Dict, Optional

from fastmcp import FastMCP

from app.mcp import memory_adapter

mcp = FastMCP(name="memory", version="1.0.0")


@mcp.tool(
    name="recall_student_history",
    description=(
        "召回某学生的长期记忆：稳定语义摘要（facts/strategies/summary）+ 最近若干条情景记忆。"
        "可选 query 做子串过滤。供 Agent Loop 在分析前补全该生历史背景，避免每次从零开始。"
    ),
)
async def recall_student_history(student_id: str, query: str = "", top_k: int = 10) -> Dict[str, Any]:
    return await memory_adapter.recall_student_history(student_id=student_id, query=query, top_k=top_k)


@mcp.tool(
    name="save_episode",
    description=(
        "写入一条情景记忆（一次预警/谈心/方案/反馈等离散事件）。metadata 可带 task_id/risk_level/type 等结构化标签。"
        "Redis 不可用时降级（saved=false），不报错。"
    ),
)
async def save_episode(student_id: str, episode: str, metadata: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    return await memory_adapter.save_episode(student_id=student_id, episode=episode, metadata=metadata)


@mcp.tool(
    name="summarize_long_term",
    description=(
        "把某学生的情景记忆蒸馏为稳定的语义记忆（facts）+ 程序记忆（有效干预 strategies）+ 画像摘要，"
        "并写回 semantic 层。LLM 不可用时降级为最近情景原文拼接（degraded=true）。"
    ),
)
async def summarize_long_term(student_id: str) -> Dict[str, Any]:
    return await memory_adapter.summarize_long_term(student_id=student_id)


def get_mcp() -> FastMCP:
    return mcp


__all__ = ["mcp", "get_mcp", "recall_student_history", "save_episode", "summarize_long_term"]
