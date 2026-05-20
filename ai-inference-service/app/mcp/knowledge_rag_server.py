"""
H-1.3：knowledge-rag MCP server 启动入口。

- 独立进程跑在端口 8095，传输 Streamable HTTP（单端点 /mcp）
- 与 ai-inference-service 主 FastAPI（8090）分进程，资源 / 重启 / 调试相互独立
- 启动方式：
    cd ai-inference-service
    python -m app.mcp.knowledge_rag_server
"""
import logging
import os

from app.core.config import settings
from app.mcp.tools import get_mcp

logger = logging.getLogger(__name__)


def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    mcp = get_mcp()
    host = os.getenv("MCP_KNOWLEDGE_RAG_HOST", "0.0.0.0")
    port = settings.MCP_KNOWLEDGE_RAG_PORT
    path = settings.MCP_KNOWLEDGE_RAG_PATH

    logger.info(
        "knowledge-rag MCP server 启动 transport=streamable-http host=%s port=%s path=%s",
        host,
        port,
        path,
    )
    # FastMCP 2.x：transport="http" 即 Streamable HTTP（spec 2025-03-26 推荐）
    mcp.run(
        transport="http",
        host=host,
        port=port,
        path=path,
    )


if __name__ == "__main__":
    main()
