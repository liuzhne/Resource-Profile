"""E-2：RAG 检索效果评估脚本。

针对 4 个 Milvus 集合（edu_cases / edu_psychology / edu_policies / edu_success）
共 50 条 golden queries（每集合 12-13 条），逐条 embedding + 向量召回 top_k=5，
统计 Recall@5（命中 / 全部）和 MRR（首个相关命中倒数排名的平均），
并按集合 + 整体输出 Markdown 报告。

容器内执行（推荐）：
    docker compose exec ai-inference-service python -m scripts.eval_retrieval

宿主机执行：
    cd ai-inference-service
    MILVUS_HOST=localhost EMBEDDING_BASE_URL=http://localhost:8092/v1 \\
    python -m scripts.eval_retrieval --output ../docs/educare/rag_eval_report.md

选项：
    --top-k       召回深度，默认 5
    --output      报告输出路径；空则只打印 stdout
    --collection  仅评估指定集合（case/psychology/policy/success），默认全部
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from app.core.config import settings
from app.services.embedding_client import get_embedding_client
from app.services.milvus_client import search

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("eval_retrieval")


# ============================================================
# Golden 数据集：50 条
#   - 每条 query 对应一个 collection 与一组 relevant_chunk_ids
#   - 同一 chunk 的多种问法都视为合法（评估 embedding 对语义相似度的稳健性）
# ============================================================
GOLDEN: List[Dict] = [
    # -------- case (13 条) --------
    {"collection": "case", "query": "GPA 突然下降伴随出勤异常如何综合干预", "relevant": ["case-001"]},
    {"collection": "case", "query": "学业成绩骤降 多维度联动干预案例", "relevant": ["case-001"]},
    {"collection": "case", "query": "班主任 周谈话 学业互助小组 家长沟通 三线并行", "relevant": ["case-001"]},
    {"collection": "case", "query": "GPA 从 3.4 跌到 2.1 怎么处理", "relevant": ["case-001"]},
    {"collection": "case", "query": "出勤异常和作业拖延 学业回升案例", "relevant": ["case-001"]},
    {"collection": "case", "query": "SCL90 量表中度焦虑 心理介入和补考", "relevant": ["case-002"]},
    {"collection": "case", "query": "心理预警 个体咨询 学业减负 朋辈陪伴", "relevant": ["case-002"]},
    {"collection": "case", "query": "中度焦虑学生 6 周干预 焦虑回归正常", "relevant": ["case-002"]},
    {"collection": "case", "query": "心理量表预警转化 挂科补考通过", "relevant": ["case-002"]},
    {"collection": "case", "query": "贫困生兼职过度导致挂科怎么办", "relevant": ["case-003"]},
    {"collection": "case", "query": "经济困难叠加学业风险 干预方案", "relevant": ["case-003"]},
    {"collection": "case", "query": "B 类困难补助 减少兼职 学业辅导 清考通过", "relevant": ["case-003"]},
    {"collection": "case", "query": "兼职 30 小时 困难补助 挂科 2 门", "relevant": ["case-003"]},
    # -------- psychology (12 条) --------
    {"collection": "psychology", "query": "动机访谈法 MI 四步流程", "relevant": ["psy-001"]},
    {"collection": "psychology", "query": "倾听 澄清 唤起 承诺 心理咨询技术", "relevant": ["psy-001"]},
    {"collection": "psychology", "query": "如何唤起学生改变意愿 不说教 自主选择", "relevant": ["psy-001"]},
    {"collection": "psychology", "query": "动机访谈 共情倾听 唤起意愿", "relevant": ["psy-001"]},
    {"collection": "psychology", "query": "认知行为疗法 应对学业焦虑", "relevant": ["psy-002"]},
    {"collection": "psychology", "query": "CBT 自动化负性思维 证据检验", "relevant": ["psy-002"]},
    {"collection": "psychology", "query": "我注定挂科 这种思维如何调整", "relevant": ["psy-002"]},
    {"collection": "psychology", "query": "行为激活 每日小目标 学业焦虑", "relevant": ["psy-002"]},
    {"collection": "psychology", "query": "共情倾听技术 反映性倾听 不评判", "relevant": ["psy-003"]},
    {"collection": "psychology", "query": "辅导员谈话避免说教和比较", "relevant": ["psy-003"]},
    {"collection": "psychology", "query": "开放式提问 沉默允许 倾听技巧", "relevant": ["psy-003"]},
    {"collection": "psychology", "query": "如何避免过早提建议 倾听要点", "relevant": ["psy-003"]},
    # -------- policy (13 条) --------
    {"collection": "policy", "query": "校内困难补助 A B C 等级标准", "relevant": ["pol-001"]},
    {"collection": "policy", "query": "特困生每月 800 元补助申请条件", "relevant": ["pol-001"]},
    {"collection": "policy", "query": "困难补助 民主公示 班委评议", "relevant": ["pol-001"]},
    {"collection": "policy", "query": "B 类一般困难每月多少钱", "relevant": ["pol-001"]},
    {"collection": "policy", "query": "家庭经济困难补助等级和额度", "relevant": ["pol-001"]},
    {"collection": "policy", "query": "学业警告触发条件 GPA 阈值", "relevant": ["pol-002"]},
    {"collection": "policy", "query": "连续两学期不及格 休学复学规则", "relevant": ["pol-002"]},
    {"collection": "policy", "query": "GPA 低于 2.0 学业警告 改进承诺书", "relevant": ["pol-002"]},
    {"collection": "policy", "query": "复学需要通过哪些评估", "relevant": ["pol-002"]},
    {"collection": "policy", "query": "勤工助学每月最多多少小时", "relevant": ["pol-003"]},
    {"collection": "policy", "query": "勤工助学岗位申请 优先条件", "relevant": ["pol-003"]},
    {"collection": "policy", "query": "时薪标准 60 小时 学生处岗位", "relevant": ["pol-003"]},
    {"collection": "policy", "query": "上学期 GPA 2.5 勤工助学申请", "relevant": ["pol-003"]},
    # -------- success (12 条) --------
    {"collection": "success", "query": "高风险学生通过综合干预转化为科研典型", "relevant": ["succ-001"]},
    {"collection": "success", "query": "三大维度同时预警 一学期 GPA 升至 3.6", "relevant": ["succ-001"]},
    {"collection": "success", "query": "学业 心理 经济三重风险后逆袭案例", "relevant": ["succ-001"]},
    {"collection": "success", "query": "院级科研立项 优秀典型 干预成功", "relevant": ["succ-001"]},
    {"collection": "success", "query": "连续旷课学生 家访 转任学习委员", "relevant": ["succ-002"]},
    {"collection": "success", "query": "出勤异常学生 灵活补考 互助学习", "relevant": ["succ-002"]},
    {"collection": "success", "query": "家庭变故引发旷课 心理疏导成功", "relevant": ["succ-002"]},
    {"collection": "success", "query": "Y 同学 4 周旷课转化典型", "relevant": ["succ-002"]},
    {"collection": "success", "query": "抑郁倾向学生成长为朋辈辅导员", "relevant": ["succ-003"]},
    {"collection": "success", "query": "心理咨询 朋辈陪伴 一年后志愿者", "relevant": ["succ-003"]},
    {"collection": "success", "query": "新生心理引导志愿者 培养路径", "relevant": ["succ-003"]},
    {"collection": "success", "query": "曾有抑郁倾向 系统咨询后转积极", "relevant": ["succ-003"]},
]


@dataclass
class QueryResult:
    query: str
    collection: str
    relevant: List[str]
    retrieved: List[str]   # top-k chunk_ids
    rank: int              # 1-based rank of first relevant; 0 = miss
    hit: bool

    @property
    def reciprocal_rank(self) -> float:
        return 1.0 / self.rank if self.rank > 0 else 0.0


async def evaluate_one(
    embed,
    item: Dict,
    top_k: int,
) -> QueryResult:
    collection_name = settings.MILVUS_COLLECTIONS[item["collection"]]
    vector = await embed.embed(item["query"])

    # 全零向量是降级标记；评估场景下应当报错而不是静默
    if vector and all(v == 0.0 for v in vector):
        log.warning("query=%r 拿到零向量降级，结果不可信", item["query"])

    hits = search(collection_name, vector, top_k=top_k)
    retrieved_ids = [h.get("chunk_id") for h in hits]

    rank = 0
    for idx, chunk_id in enumerate(retrieved_ids, start=1):
        if chunk_id in item["relevant"]:
            rank = idx
            break

    return QueryResult(
        query=item["query"],
        collection=item["collection"],
        relevant=item["relevant"],
        retrieved=retrieved_ids,
        rank=rank,
        hit=rank > 0,
    )


def aggregate(results: List[QueryResult]) -> Dict[str, float]:
    if not results:
        return {"recall_at_k": 0.0, "mrr": 0.0, "n": 0}
    n = len(results)
    recall = sum(1 for r in results if r.hit) / n
    mrr = sum(r.reciprocal_rank for r in results) / n
    return {"recall_at_k": recall, "mrr": mrr, "n": n}


def render_report(
    by_collection: Dict[str, List[QueryResult]],
    overall: Dict[str, float],
    top_k: int,
) -> str:
    lines: List[str] = []
    lines.append(f"# RAG 检索效果评估报告")
    lines.append("")
    lines.append(f"- 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- Embedding 模型: `{settings.EMBEDDING_MODEL}` (dim={settings.EMBEDDING_DIM})")
    lines.append(f"- Milvus: `{settings.MILVUS_HOST}:{settings.MILVUS_PORT}`")
    lines.append(f"- top_k = {top_k}")
    lines.append(f"- 总查询数: {overall['n']}")
    lines.append("")
    lines.append("## 整体指标")
    lines.append("")
    lines.append(f"| 指标 | 数值 |")
    lines.append(f"|---|---|")
    lines.append(f"| Recall@{top_k} | **{overall['recall_at_k']:.3f}** |")
    lines.append(f"| MRR | **{overall['mrr']:.3f}** |")
    lines.append("")

    lines.append("## 分集合指标")
    lines.append("")
    lines.append(f"| 集合 | N | Recall@{top_k} | MRR |")
    lines.append(f"|---|---|---|---|")
    for col, results in by_collection.items():
        agg = aggregate(results)
        lines.append(
            f"| {settings.MILVUS_COLLECTIONS[col]} ({col}) | {agg['n']} | "
            f"{agg['recall_at_k']:.3f} | {agg['mrr']:.3f} |"
        )
    lines.append("")

    lines.append("## 失败 / 低名次明细")
    lines.append("")
    lines.append("> 仅展示未命中或首个相关命中位于 top-3 之外的条目；用于诊断 embedding 漂移与边界问题。")
    lines.append("")
    failures: List[QueryResult] = []
    for results in by_collection.values():
        for r in results:
            if r.rank == 0 or r.rank > 3:
                failures.append(r)
    if not failures:
        lines.append("_（无）_")
    else:
        lines.append(f"| 集合 | Query | 期望 chunk_id | top-{top_k} 召回 | 首中位次 |")
        lines.append(f"|---|---|---|---|---|")
        for r in failures:
            ranked = " > ".join(r.retrieved) if r.retrieved else "(空)"
            rank_label = "MISS" if r.rank == 0 else str(r.rank)
            lines.append(
                f"| {r.collection} | {r.query} | {','.join(r.relevant)} | {ranked} | {rank_label} |"
            )
    lines.append("")
    return "\n".join(lines)


async def run(top_k: int, output: Optional[Path], only: Optional[str]) -> int:
    items = GOLDEN if not only else [g for g in GOLDEN if g["collection"] == only]
    if not items:
        log.error("没有匹配的 golden 数据：collection=%s", only)
        return 1
    log.info("评估 golden queries: %d 条，top_k=%d", len(items), top_k)

    embed = get_embedding_client()
    results: List[QueryResult] = []
    by_collection: Dict[str, List[QueryResult]] = {}
    try:
        for idx, item in enumerate(items, start=1):
            r = await evaluate_one(embed, item, top_k)
            results.append(r)
            by_collection.setdefault(item["collection"], []).append(r)
            log.info(
                "[%d/%d] %s | rank=%s | %s",
                idx, len(items), item["collection"],
                r.rank if r.rank else "MISS", item["query"][:30],
            )
    finally:
        await embed.close()

    overall = aggregate(results)
    report = render_report(by_collection, overall, top_k)

    print(report)

    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(report, encoding="utf-8")
        log.info("报告已写入 %s", output)

    log.info(
        "完成：Recall@%d=%.3f  MRR=%.3f  (N=%d)",
        top_k, overall["recall_at_k"], overall["mrr"], overall["n"],
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="EduCare RAG 检索效果评估")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--output", type=Path, default=None,
                        help="Markdown 报告输出路径；缺省仅打印 stdout")
    parser.add_argument("--collection", type=str, default=None,
                        choices=["case", "psychology", "policy", "success"])
    args = parser.parse_args()
    return asyncio.run(run(args.top_k, args.output, args.collection))


if __name__ == "__main__":
    sys.exit(main())
