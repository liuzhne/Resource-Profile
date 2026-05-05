"""Milvus 4 集合初始化（幂等）。

容器内执行：
    docker compose exec ai-inference-service python -m scripts.init_milvus

宿主机执行：
    cd ai-inference-service
    MILVUS_HOST=localhost python -m scripts.init_milvus
"""
import logging
import sys

from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    connections,
    utility,
)

from app.core.config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("init_milvus")

INDEX_PARAMS = {
    "metric_type": "IP",
    "index_type": "IVF_FLAT",
    "params": {"nlist": 128},
}


def build_schema(collection_name: str) -> CollectionSchema:
    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="title", dtype=DataType.VARCHAR, max_length=255),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=4096),
        FieldSchema(name="source", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=settings.EMBEDDING_DIM),
    ]
    return CollectionSchema(fields, description=f"EduCare 知识库 - {collection_name}")


def main() -> int:
    log.info("连接 Milvus %s:%s", settings.MILVUS_HOST, settings.MILVUS_PORT)
    try:
        connections.connect(host=settings.MILVUS_HOST, port=settings.MILVUS_PORT, timeout=10)
    except Exception as exc:
        log.error("连接失败: %s", exc)
        return 1

    for source_key, name in settings.MILVUS_COLLECTIONS.items():
        if utility.has_collection(name):
            log.info("集合 %s 已存在，跳过创建", name)
            continue
        col = Collection(name, build_schema(name))
        col.create_index(field_name="embedding", index_params=INDEX_PARAMS)
        log.info("集合 %s 创建完成（dim=%d）", name, settings.EMBEDDING_DIM)
    return 0


if __name__ == "__main__":
    sys.exit(main())
