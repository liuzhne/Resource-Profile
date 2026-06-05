# I-1 Hybrid Retrieval 设计（dense + BM25 + RRF）

> 创建：2026-06-05 ｜ 对应 EXECUTION_PLAN I-1.1 ~ I-1.4

## 1. I-1.1 决策：ES 8.x vs Milvus 内置 BM25 vs 进程内 BM25

| 方案 | 优点 | 顾虑 |
|------|------|------|
| Elasticsearch 8.x | 工业级 BM25 + 成熟 | 新起一个重容器（JVM，内存大户），与"复用既有栈"相悖；双写/同步成本 |
| Milvus 2.4.1 原生 BM25 | 同库、无双写 | **原生 BM25 函数是 Milvus 2.5+**；2.4.1 仅支持 sparse 向量（要自己算 BGE-M3 稀疏），改 schema + 重灌数据 |
| **进程内 BM25 over dense 池（选定）** | 零新依赖、零 schema 改动、可离线单测 | 纯词法-only 文档若不在 dense 池内召不回（用更大 recall 缓解）；非全量倒排，规模大时不如 ES |

**决策：进程内 BM25 + RRF，在 dense 召回候选池上做词法重排**。理由与项目既有取舍一致（G-5/G-6/H-1.3/H-5 都"复用既有栈、避开重组件"）：
- resume 项目不值得为 BM25 起 ES 容器或重灌 Milvus；
- I-1 的验收是"专有名词 query top-3 命中率 ≥ 纯 dense + 15%"——dense 召回一个**更大的候选池**（`RAG_RECALL_EXPAND`），BM25 在池内把含精确 token（"高数 II""SCL-90"）的片段顶上来，正好命中该场景；
- **接口不变**的升级路径：未来规模上来，把 `hybrid_retrieval.fuse_hits` 背后换成 Milvus 2.5 原生 BM25 或 ES，`rag_pipeline` 调用点不动。

## 2. I-1.2 实现

`app/services/hybrid_retrieval.py`（纯函数，无 IO，全单测覆盖）：
- `tokenize`：中英混排分词 —— ASCII alnum 串 + CJK 单字 + CJK 邻接 bigram（让 "高数" 这类双字词成 token）
- `BM25`：自实现 BM25Okapi（k1=1.5, b=0.75，idf 加 1 平滑），`score/rank`
- `rrf_fuse`：Reciprocal Rank Fusion，Σ 1/(k+rank)，k=60
- `fuse_hits(query, hits)`：对 dense 有序 hits 做 BM25 重排 → 与 dense 序 RRF 融合 → 回填 `hybrid_rank`

## 3. I-1.3 接入与灰度

`rag_pipeline.retrieve_from_collection` 加 `enable_hybrid` 参数 + 配置 `RAG_HYBRID_ENABLED`（默认 **false**，上线前行为不变）：
- dense 召回候选池后，若开启则 `fuse_hits` 重排，再走（可选）reranker、截断 top_k
- payload 增 `hybrid` 字段；缓存 key 区分 dense/hybrid，互不污染
- 灰度：先 `RAG_HYBRID_ENABLED=true` 在测试环境对比，knowledge-rag MCP 三个 search tool 自动透传（无需改 tool 代码）

> 注意：若 reranker（cross-encoder）开启，它会再次按相关性排序、覆盖 hybrid 顺序；hybrid 在 reranker 关闭时直接决定最终序。两者可叠加评估。

## 4. I-1.4 离线评测

`eval/hybrid_eval.py` + `eval/hybrid_queries.jsonl`：对每条专有名词 query 分别以 dense / hybrid 跑 `retrieve_from_collection`，看期望 `chunk_id` 或关键词是否落在 **top-3**，输出两种模式命中率 + 提升。

**为何不用 RAGAS**：RAGAS 的 context_precision/answer_relevancy 需额外 LLM 评审 + 重依赖，对"专有名词召回"这个具体验收点是过度工程；本项目用 **top-3 命中率**直接对齐 I-1 验收标准（≥ 纯 dense + 15%）。RAGAS 作为未来更全面评测的可选项保留。

要求：Milvus 在线且对应 collection 已灌数据（`/api/v1/rag/upsert`）。用法：
```bash
RAG_HYBRID_ENABLED=false python eval/hybrid_eval.py   # 不影响，脚本内部按模式逐条覆盖
python eval/hybrid_eval.py --collection case --top-k 3
```

## 5. 已知限制
- 候选池外的纯词法文档召不回（非全量倒排）；靠 `RAG_RECALL_EXPAND` 放大池子缓解
- BM25 分词为轻量规则（无词典/停用词表），中文长词靠 bigram 近似
- reranker 开启时 hybrid 顺序被覆盖（见 §3 注）
