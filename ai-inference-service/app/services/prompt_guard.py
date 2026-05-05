"""
Prompt 注入防护：与 Java 端 com.edu.common.util.PromptSanitizer 行为对齐。
- 去掉控制字符（\\x00-\\x08, \\x0B-\\x1F, \\x7F）
- 剥掉行首角色前缀（system:/assistant:/user:/tool:/系统:/助手:）
- 单字段长度上限 500，超长截断 + …[truncated]
- wrap(tag, content) 用 XML 标签包裹，便于 system prompt 声明「这是数据」
"""
import re
from typing import Any

DEFAULT_MAX_FIELD_LEN = 500

_CTRL_CHAR = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")
_ROLE_LINE = re.compile(
    r"^\s*(system|assistant|user|tool|系统|助手)\s*[:：]\s*",
    re.IGNORECASE | re.MULTILINE,
)
_TAG_SAFE = re.compile(r"[^A-Za-z0-9_]")


def sanitize_string(value: str, max_len: int = DEFAULT_MAX_FIELD_LEN) -> str:
    if value is None:
        return value
    s = _CTRL_CHAR.sub(" ", value)
    s = _ROLE_LINE.sub("", s)
    if len(s) > max_len:
        s = s[:max_len] + "…[truncated]"
    return s


def sanitize(node: Any) -> Any:
    """递归清理 dict/list/str 叶子节点。"""
    if node is None:
        return None
    if isinstance(node, str):
        return sanitize_string(node)
    if isinstance(node, dict):
        return {str(k): sanitize(v) for k, v in node.items()}
    if isinstance(node, list):
        return [sanitize(item) for item in node]
    return node


def wrap(tag: str, content: str) -> str:
    safe_tag = _TAG_SAFE.sub("", tag) if tag else "data"
    if not safe_tag:
        safe_tag = "data"
    return f"<{safe_tag}>\n{content or ''}\n</{safe_tag}>"
