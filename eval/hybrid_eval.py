#!/usr/bin/env python3
"""I-1.4：Hybrid vs Dense 离线评测（top-k 命中率）。

对 eval/hybrid_queries.jsonl 里的专有名词 query，分别以 dense / hybrid 跑
rag_pipeline.retrieve_from_collection，看期望关键词（或 chunk_id）是否落在 top-k，
输出两种模式命中率 + 提升。对齐 I-1 验收：专有名词 top-3 命中率 ≥ 纯 dense + 15%。

要求：Milvus 在线 + 对应 collection 已灌数据（/api/v1/rag/upsert）。
直接 import app.services.rag_pipeline，故脚本会把 ai-inference-service 加入 sys.path。

用法：
  python eval/hybrid_eval.py --top-k 3
  python eval/hybrid_eval.py --input eval/hybrid_queries.jsonl --top-k 3
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path
from typing import Any, Dict, List

# 把 ai-inference-service 加入 path，便于直接复用其 services
_REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_REPO / "ai-inference-service"))


def hit_at_k(chunks: List[Dict[str, Any]], case: Dict[str, Any], k: int) -> bool:
    """top-k 内任一 chunk 命中期望 chunk_id 或全部期望关键词其一即算命中。"""
    top = chunks[:k]
    expected_id = case.get("expected_chunk_id")
    keywords = case.get("expected_keywords") or []
    for ch in top:
        if expected_id and str(ch.get("chunk_id")) == str(expected_id):
            return True
        blob = f"{ch.get('title') or ''}\n{ch.get('content') or ''}".lower()
        if any(kw.lower() in blob for kw in keywords):
            return True
    return False


async def _run(args: argparse.Namespace) -> int:
    from app.services.rag_pipeline import retrieve_from_collection  # 延迟 import（拉 milvus 等）

    cases = [json.loads(l) for l in Path(args.input).read_text(encoding="utf-8").splitlines() if l.strip()]
    print(f"载入 {len(cases)} 条 query，top_k={args.top_k}")

    dense_hits = 0
    hybrid_hits = 0
    for c in cases:
        coll = c["collection"]
        q = c["query"]
        dense = await retrieve_from_collection(q, coll, top_k=args.top_k, enable_hybrid=False)
        hybrid = await retrieve_from_collection(q, coll, top_k=args.top_k, enable_hybrid=True)
        d_ok = hit_at_k(dense.get("chunks", []), c, args.top_k)
        h_ok = hit_at_k(hybrid.get("chunks", []), c, args.top_k)
        dense_hits += int(d_ok)
        hybrid_hits += int(h_ok)
        print(f"  {c['id']:<8} [{coll:<10}] dense={'✓' if d_ok else '✗'} hybrid={'✓' if h_ok else '✗'}  {q[:30]}")

    n = len(cases) or 1
    d_rate = dense_hits / n
    h_rate = hybrid_hits / n
    delta = h_rate - d_rate
    print("\n=== 汇总 ===")
    print(f"dense  top-{args.top_k} 命中率: {d_rate:.2%} ({dense_hits}/{n})")
    print(f"hybrid top-{args.top_k} 命中率: {h_rate:.2%} ({hybrid_hits}/{n})")
    print(f"提升: {delta:+.2%}（验收目标 ≥ +15%）")
    return 0 if delta >= 0 else 1


def main() -> int:
    p = argparse.ArgumentParser(description="Hybrid vs Dense 召回评测")
    p.add_argument("--input", default="eval/hybrid_queries.jsonl")
    p.add_argument("--top-k", type=int, default=3)
    args = p.parse_args()
    return asyncio.run(_run(args))


if __name__ == "__main__":
    sys.exit(main())
