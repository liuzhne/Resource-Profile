"""LLM 输出 JSON 提取工具：处理 ```json ``` 围栏与首尾杂质。"""
import json
import logging
import re
from typing import Any, Dict

logger = logging.getLogger(__name__)

_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*?\}|\[.*?\])\s*```", re.DOTALL)
_BRACE_RE = re.compile(r"\{.*\}|\[.*\]", re.DOTALL)


def extract_json(text: str) -> Dict[str, Any]:
    """从 LLM 自由文本中尝试解析出 JSON 对象；失败返回空字典。"""
    if not text:
        return {}

    fenced = _FENCE_RE.search(text)
    candidate = fenced.group(1) if fenced else None

    if candidate is None:
        m = _BRACE_RE.search(text)
        candidate = m.group(0) if m else text.strip()

    try:
        return json.loads(candidate)
    except json.JSONDecodeError as exc:
        logger.warning("JSON 解析失败: %s | 原文: %s", exc, text[:200])
        return {}
