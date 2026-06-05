"""I-1.2：Hybrid 检索（dense + BM25 + RRF 融合）。

设计取舍（决策见 HYBRID_RETRIEVAL_DESIGN.md / EXECUTION_PLAN I-1.1）：
- **不引 Elasticsearch 容器**，也不改 Milvus schema（2.4.1 原生 BM25 函数要 2.5+）。
- 在 dense 召回得到的**候选池**上做 BM25 词法打分，再用 RRF 把 dense 排序与 BM25 排序融合。
  对"专有名词" query（如 "高数 II"、"SCL-90"）尤其有效：embedding 常丢精确 token，
  BM25 能把池内含精确词的片段顶上来。代价：纯词法-only 的文档若不在 dense 池内仍召不回
  （用更大 recall 池缓解）。生产可升级到 Milvus 2.5 原生 BM25 / ES，接口不变。

BM25 为自实现（无 rank_bm25 依赖），中英混排 tokenizer：ASCII alnum token + CJK 单字 + CJK 邻接 bigram。
本模块全部为纯函数，无 IO，可离线单测。
"""
import math
import re
from collections import Counter
from typing import Any, Dict, List, Sequence

_ASCII = re.compile(r"[a-z0-9]+")
_CJK = re.compile(r"[一-鿿]")


def tokenize(text: str) -> List[str]:
    """中英混排分词：小写 ASCII alnum 串 + CJK 单字 + CJK 邻接 bigram。"""
    if not text:
        return []
    s = text.lower()
    tokens: List[str] = _ASCII.findall(s)
    cjk = _CJK.findall(s)
    tokens.extend(cjk)
    for a, b in zip(cjk, cjk[1:]):
        tokens.append(a + b)
    return tokens


class BM25:
    """BM25Okapi（k1/b 可调）。corpus 为已分词的文档列表。"""

    def __init__(self, corpus_tokens: Sequence[List[str]], k1: float = 1.5, b: float = 0.75):
        self.k1 = k1
        self.b = b
        self.n = len(corpus_tokens)
        self.doc_len = [len(d) for d in corpus_tokens]
        self.avgdl = (sum(self.doc_len) / self.n) if self.n else 0.0
        self.tf: List[Counter] = [Counter(d) for d in corpus_tokens]
        df: Dict[str, int] = {}
        for d in corpus_tokens:
            for t in set(d):
                df[t] = df.get(t, 0) + 1
        # idf 加 1 平滑，避免高频词出现负值导致排序异常
        self.idf = {t: math.log(1 + (self.n - n + 0.5) / (n + 0.5)) for t, n in df.items()}

    def score(self, query_tokens: Sequence[str], idx: int) -> float:
        if self.avgdl == 0 or idx >= self.n:
            return 0.0
        dl = self.doc_len[idx]
        tf = self.tf[idx]
        s = 0.0
        for t in query_tokens:
            f = tf.get(t)
            if not f:
                continue
            idf = self.idf.get(t, 0.0)
            s += idf * (f * (self.k1 + 1)) / (f + self.k1 * (1 - self.b + self.b * dl / self.avgdl))
        return s

    def rank(self, query_tokens: Sequence[str]) -> List[int]:
        """返回按 BM25 分数降序的文档下标（稳定：同分按原序）。"""
        scored = [(i, self.score(query_tokens, i)) for i in range(self.n)]
        scored.sort(key=lambda x: x[1], reverse=True)
        return [i for i, _ in scored]


def rrf_fuse(rank_lists: Sequence[Sequence[int]], k: int = 60) -> List[int]:
    """Reciprocal Rank Fusion：输入多个有序下标列表，输出融合后的下标顺序。

    每个文档得分 = Σ 1/(k + rank)（rank 从 0 起）。未在某列表出现则该列表不贡献。
    """
    scores: Dict[int, float] = {}
    for lst in rank_lists:
        for rank, idx in enumerate(lst):
            scores[idx] = scores.get(idx, 0.0) + 1.0 / (k + rank)
    return [idx for idx, _ in sorted(scores.items(), key=lambda x: x[1], reverse=True)]


def fuse_hits(
    query: str,
    hits: List[Dict[str, Any]],
    content_key: str = "content",
    title_key: str = "title",
    k: int = 60,
) -> List[Dict[str, Any]]:
    """对 dense 召回的 hits（已按 dense 分数有序）做 BM25 + RRF 重排，返回重排后的 hits。

    hits 为空或仅 1 条时原样返回。每条 hit 额外打上 `hybrid_rank`（融合后名次，从 0 起）。
    """
    if not hits or len(hits) == 1:
        return hits
    corpus = [tokenize(str(h.get(content_key) or h.get(title_key) or "")) for h in hits]
    q_tokens = tokenize(query)
    dense_order = list(range(len(hits)))  # hits 已是 dense 序
    bm25_order = BM25(corpus).rank(q_tokens)
    fused = rrf_fuse([dense_order, bm25_order], k=k)
    out: List[Dict[str, Any]] = []
    for rank, idx in enumerate(fused):
        row = dict(hits[idx])
        row["hybrid_rank"] = rank
        out.append(row)
    return out
