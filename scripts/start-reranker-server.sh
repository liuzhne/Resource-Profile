#!/usr/bin/env bash
# llama.cpp BGE Reranker Server (Metal GPU 加速)
# 端口 8093，提供 Cohere 兼容 /rerank
#
# 前置条件：
#   1. 已编译 llama.cpp（建议 2024-09 之后版本，需含 --reranking 参数支持）
#      若启动报 "unrecognized argument '--reranking'"，请 git pull && cmake --build 重编译
#   2. 已下载 BGE-reranker-base GGUF 模型
#      搜索关键词："bge-reranker-base gguf" 在 HuggingFace
#      默认期望路径：~/edu-ai/models/bge-reranker-base-q8_0.gguf
#
# 用法：
#   bash scripts/start-reranker-server.sh
#   RERANKER_MODEL_PATH=/path/to/model.gguf bash scripts/start-reranker-server.sh
set -e

LLAMA_BIN="${LLAMA_BIN:-$HOME/edu-ai/llama.cpp/build/bin/llama-server}"
MODEL="${RERANKER_MODEL_PATH:-$HOME/edu-ai/models/bge-reranker-base-q8_0.gguf}"
PORT="${RERANKER_PORT:-8093}"
CTX="${RERANKER_CTX:-512}"

if [[ ! -x "$LLAMA_BIN" ]]; then
    echo "✗ 找不到可执行的 llama-server: $LLAMA_BIN"
    exit 1
fi
if [[ ! -f "$MODEL" ]]; then
    echo "✗ 找不到模型文件: $MODEL"
    echo "  请下载 BGE-reranker-base GGUF，或设置 RERANKER_MODEL_PATH"
    exit 1
fi

echo "▶ 启动 BGE Reranker Server (Metal GPU)"
echo "  llama-server : $LLAMA_BIN"
echo "  model        : $MODEL"
echo "  port         : $PORT"
echo "  context      : $CTX"
echo "  endpoint     : http://localhost:$PORT/rerank"

exec "$LLAMA_BIN" \
    -m "$MODEL" \
    --host 0.0.0.0 \
    --port "$PORT" \
    --reranking \
    -c "$CTX" \
    --threads 4 \
    --n-gpu-layers 999
