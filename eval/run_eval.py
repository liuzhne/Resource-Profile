#!/usr/bin/env python3
"""G-6.3：风险识别离线 eval 跑分器。

从 risk_assessment.jsonl 读用例 → POST /api/v1/agent/risk → 计算两个指标：

  1. **等级一致率 (level_accuracy)**：
     risk_level 完全匹配 → 1；相邻一档（none↔low, low↔medium, medium↔high）→ 0.5；
     差两档及以上 → 0。整体取均值。

  2. **关键短语命中率 (phrase_hit_rate)**：
     把 root_cause_analysis + key_indicators 拼成全文，检查 expected.key_phrases 各短语
     的 substring 命中比例（大小写、空格不敏感）。逐用例算命中率，再取均值。

用法：
  python eval/run_eval.py
  python eval/run_eval.py --input eval/risk_assessment.jsonl \\
      --base-url http://localhost:8090 --concurrency 4 \\
      --out eval/run_results.json --md eval/run_results.md

要求服务在跑（默认指向 ai-inference-service:8090）。LLM 自身的下游 llama.cpp 不开
也能跑，但每条都会走 fallback（risk_level=medium），可用于自检脚本逻辑。
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional

# httpx 只在实际跑分时才需要；纯函数（_level_score 等）可在无 httpx 环境验证
def _require_httpx():
    try:
        import httpx  # noqa: F401
        return httpx
    except ImportError:
        print("需要 httpx：pip install httpx", file=sys.stderr)
        sys.exit(2)


LEVEL_ORDER = ["none", "low", "medium", "high"]


def _level_score(predicted: str, expected: str) -> float:
    """完全匹配 1.0；相邻 0.5；其他 0.0。未知 label 0.0。"""
    if predicted not in LEVEL_ORDER or expected not in LEVEL_ORDER:
        return 0.0
    if predicted == expected:
        return 1.0
    if abs(LEVEL_ORDER.index(predicted) - LEVEL_ORDER.index(expected)) == 1:
        return 0.5
    return 0.0


def _phrase_hits(text: str, phrases: List[str]) -> tuple[int, int]:
    """返回 (命中数, 总数)。空 phrases 返回 (0, 0)，调用方按 1.0 处理。"""
    if not phrases:
        return 0, 0
    blob = (text or "").lower()
    hits = sum(1 for p in phrases if p and p.lower() in blob)
    return hits, len(phrases)


def _flatten_response(resp: Dict[str, Any]) -> str:
    """把 risk_level 之外可能含"理由"的字段拼成一个 blob，用于关键词命中。"""
    parts = [
        str(resp.get("primary_risk_type") or ""),
        str(resp.get("root_cause_analysis") or ""),
        str(resp.get("urgency_reason") or ""),
    ]
    indicators = resp.get("key_indicators") or []
    if isinstance(indicators, list):
        parts.extend(str(x) for x in indicators)
    interventions = resp.get("recommended_intervention_types") or []
    if isinstance(interventions, list):
        parts.extend(str(x) for x in interventions)
    return "\n".join(parts)


async def _call_one(
    client,  # httpx.AsyncClient (lazy import in main)
    base_url: str,
    case: Dict[str, Any],
    timeout: float,
) -> Dict[str, Any]:
    case_id = case.get("id", "?")
    expected = case.get("expected", {})
    payload = case.get("input", {})
    start = time.perf_counter()
    try:
        resp = await client.post(
            f"{base_url.rstrip('/')}/api/v1/agent/risk",
            json=payload,
            timeout=timeout,
        )
        resp.raise_for_status()
        body = resp.json()
    except Exception as exc:
        return {
            "id": case_id,
            "ok": False,
            "error": str(exc)[:200],
            "elapsed_ms": (time.perf_counter() - start) * 1000,
        }
    elapsed_ms = (time.perf_counter() - start) * 1000

    predicted_level = str(body.get("risk_level") or "").lower()
    expected_level = str(expected.get("risk_level") or "").lower()
    level_score = _level_score(predicted_level, expected_level)

    blob = _flatten_response(body)
    phrases = expected.get("key_phrases") or []
    hits, total = _phrase_hits(blob, phrases)
    phrase_hit_rate = (hits / total) if total else 1.0

    return {
        "id": case_id,
        "ok": True,
        "expected_level": expected_level,
        "predicted_level": predicted_level,
        "level_match": predicted_level == expected_level,
        "level_score": level_score,
        "phrase_hits": hits,
        "phrase_total": total,
        "phrase_hit_rate": phrase_hit_rate,
        "elapsed_ms": elapsed_ms,
    }


async def _run(args: argparse.Namespace) -> Dict[str, Any]:
    httpx = _require_httpx()
    cases = [json.loads(line) for line in Path(args.input).read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"载入 {len(cases)} 条用例，base_url={args.base_url}，并发={args.concurrency}")

    sem = asyncio.Semaphore(args.concurrency)
    results: List[Dict[str, Any]] = []

    async with httpx.AsyncClient() as client:
        async def _guarded(case):
            async with sem:
                return await _call_one(client, args.base_url, case, args.timeout)

        tasks = [_guarded(c) for c in cases]
        for i, task in enumerate(asyncio.as_completed(tasks), 1):
            r = await task
            results.append(r)
            tag = "OK" if r["ok"] else "ERR"
            extra = f"L={r.get('predicted_level','-')}/{r.get('expected_level','-')} P={r.get('phrase_hits','?')}/{r.get('phrase_total','?')}" if r["ok"] else r.get("error", "")
            print(f"  [{i:>3}/{len(cases)}] {r['id']:<10} {tag} {extra}")

    return _summarize(results)


def _summarize(results: List[Dict[str, Any]]) -> Dict[str, Any]:
    ok = [r for r in results if r["ok"]]
    err = [r for r in results if not r["ok"]]
    by_level: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for r in ok:
        by_level[r["expected_level"]].append(r)

    confusion = Counter()
    for r in ok:
        confusion[(r["expected_level"], r["predicted_level"])] += 1

    def avg(xs, key):
        return (sum(x[key] for x in xs) / len(xs)) if xs else 0.0

    summary = {
        "total": len(results),
        "ok": len(ok),
        "err": len(err),
        "level_accuracy_exact": avg(ok, "level_match"),
        "level_score_weighted": avg(ok, "level_score"),
        "phrase_hit_rate": avg(ok, "phrase_hit_rate"),
        "by_level": {
            lvl: {
                "count": len(rs),
                "exact": avg(rs, "level_match"),
                "weighted": avg(rs, "level_score"),
                "phrase": avg(rs, "phrase_hit_rate"),
            }
            for lvl, rs in by_level.items()
        },
        "confusion": {f"{e}->{p}": n for (e, p), n in sorted(confusion.items())},
        "errors": [{"id": r["id"], "error": r.get("error")} for r in err],
        "results": results,
    }
    return summary


def _print_report(summary: Dict[str, Any]) -> None:
    print("\n=== 汇总 ===")
    print(f"总数 {summary['total']}  成功 {summary['ok']}  失败 {summary['err']}")
    print(f"等级一致率（exact）         : {summary['level_accuracy_exact']:.2%}")
    print(f"等级加权分（相邻 0.5）       : {summary['level_score_weighted']:.2%}")
    print(f"关键短语命中率             : {summary['phrase_hit_rate']:.2%}")
    print("\n按期望等级分桶：")
    for lvl in LEVEL_ORDER:
        b = summary["by_level"].get(lvl)
        if not b:
            continue
        print(f"  {lvl:<6}  n={b['count']:<2}  exact={b['exact']:.0%}  weighted={b['weighted']:.0%}  phrase={b['phrase']:.0%}")
    if summary["errors"]:
        print("\n失败用例：")
        for e in summary["errors"]:
            print(f"  {e['id']}: {e['error']}")


def _write_md(summary: Dict[str, Any], path: Path) -> None:
    lines = [
        "# Risk Eval 报告",
        f"\n- 总数：{summary['total']}  成功：{summary['ok']}  失败：{summary['err']}",
        f"- **等级一致率（exact）**：{summary['level_accuracy_exact']:.2%}",
        f"- **等级加权分（相邻 0.5）**：{summary['level_score_weighted']:.2%}",
        f"- **关键短语命中率**：{summary['phrase_hit_rate']:.2%}",
        "\n## 按期望等级分桶",
        "| 等级 | 样本数 | exact | weighted | 短语命中 |",
        "|------|--------|-------|----------|----------|",
    ]
    for lvl in LEVEL_ORDER:
        b = summary["by_level"].get(lvl)
        if not b:
            continue
        lines.append(f"| {lvl} | {b['count']} | {b['exact']:.0%} | {b['weighted']:.0%} | {b['phrase']:.0%} |")

    if summary["confusion"]:
        lines.append("\n## 混淆矩阵（expected -> predicted）")
        for k, v in summary["confusion"].items():
            lines.append(f"- `{k}` : {v}")

    if summary["errors"]:
        lines.append("\n## 失败用例")
        for e in summary["errors"]:
            lines.append(f"- **{e['id']}**: {e['error']}")

    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="EduCare risk eval runner")
    p.add_argument("--input", default="eval/risk_assessment.jsonl")
    p.add_argument("--base-url", default="http://localhost:8090")
    p.add_argument("--concurrency", type=int, default=4)
    p.add_argument("--timeout", type=float, default=90.0)
    p.add_argument("--out", default="eval/run_results.json")
    p.add_argument("--md", default=None, help="可选 markdown 报告输出路径")
    args = p.parse_args()

    summary = asyncio.run(_run(args))
    _print_report(summary)

    Path(args.out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nJSON 报告 -> {args.out}")
    if args.md:
        _write_md(summary, Path(args.md))
        print(f"MD  报告 -> {args.md}")

    # 退出码：等级一致率 < 0.6 视为失败（CI 可用）
    threshold = 0.6
    if summary["level_accuracy_exact"] < threshold:
        print(f"\n✗ 等级一致率 {summary['level_accuracy_exact']:.2%} 低于阈值 {threshold:.0%}")
        return 1
    print(f"\n✓ 等级一致率 {summary['level_accuracy_exact']:.2%} ≥ 阈值 {threshold:.0%}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
