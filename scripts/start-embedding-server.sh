#!/usr/bin/env bash
# llama.cpp BGE Embedding Server (Metal GPU 加速)
# 端口 8092，提供 OpenAI 兼容 /v1/embeddings
#
# 前置条件：
#   1. 已编译 llama.cpp（目录默认 ~/edu-ai/llama.cpp）
#   2. 已下载 BGE-large-zh-v1.5 GGUF 模型，建议 q8_0 量化
#      搜索关键词："bge-large-zh-v1.5 gguf" 在 HuggingFace
#      默认期望路径：~/edu-ai/models/bge-large-zh-v1.5-q8_0.gguf
#
# 用法：
#   bash scripts/start-embedding-server.sh
#   EMBEDDING_MODEL_PATH=/path/to/model.gguf bash scripts/start-embedding-server.sh
set -e

LLAMA_BIN="${LLAMA_BIN:-$HOME/edu-ai/llama.cpp/build/bin/llama-server}"
MODEL="${EMBEDDING_MODEL_PATH:-$HOME/edu-ai/models/bge-large-zh-v1.5-q8_0.gguf}"
PORT="${EMBEDDING_PORT:-8092}"
CTX="${EMBEDDING_CTX:-512}"

if [[ ! -x "$LLAMA_BIN" ]]; then
    echo "✗ 找不到可执行的 llama-server: $LLAMA_BIN"
    echo "  请编译 llama.cpp 或设置 LLAMA_BIN 环境变量"
    exit 1
fi
if [[ ! -f "$MODEL" ]]; then
    echo "✗ 找不到模型文件: $MODEL"
    echo "  请下载 BGE-large-zh-v1.5 GGUF (推荐 q8_0)，或设置 EMBEDDING_MODEL_PATH"
    exit 1
fi

echo "▶ 启动 BGE Embedding Server (Metal GPU)"
echo "  llama-server : $LLAMA_BIN"
echo "  model        : $MODEL"
echo "  port         : $PORT"
echo "  context      : $CTX"
echo "  endpoint     : http://localhost:$PORT/v1/embeddings"

exec "$LLAMA_BIN" \
    -m "$MODEL" \
    --host 0.0.0.0 \
    --port "$PORT" \
    --embeddings \
    --pooling cls \
    -c "$CTX" \
    --threads 4 \
    --n-gpu-layers 999
