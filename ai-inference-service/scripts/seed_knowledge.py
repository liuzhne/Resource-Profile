"""向 4 个 Milvus 集合写入种子知识数据（每集合 3 条，共 12 条）。

embedding 调用失败时使用零向量降级（仅保证链路可跑通；召回质量需接入真实 BGE）。

容器内执行：
    docker compose exec ai-inference-service python -m scripts.seed_knowledge

宿主机执行：
    cd ai-inference-service
    MILVUS_HOST=localhost EMBEDDING_BASE_URL=http://localhost:8091/v1 \
    python -m scripts.seed_knowledge
"""
import asyncio
import logging
import sys

from pymilvus import Collection, connections, utility

from app.core.config import settings
from app.services.embedding_client import get_embedding_client

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("seed_knowledge")


SEED = {
    "case": [
        {
            "chunk_id": "case-001",
            "title": "GPA 骤降的多维综合干预",
            "content": "学生 X 大二上学期 GPA 由 3.4 骤降至 2.1，伴随出勤异常与作业拖延。班主任启动周谈话 + 学业互助小组 + 家长沟通三线并行，第 4 周成绩回升至 2.9，行为表现稳定。",
        },
        {
            "chunk_id": "case-002",
            "title": "心理预警转化案例",
            "content": "SCL90 量表显示中度焦虑学生，心理中心 1 周内介入个体咨询 + 学业减负 + 朋辈陪伴，6 周后焦虑量表回归正常区间，挂科科目顺利通过补考。",
        },
        {
            "chunk_id": "case-003",
            "title": "经济困难叠加学业风险",
            "content": "贫困生因兼职过度导致挂科 2 门，辅导员协助申请 B 类困难补助（500 元/月）+ 减少兼职至 30 小时/月 + 配套学业辅导，本学期清考全部通过。",
        },
    ],
    "psychology": [
        {
            "chunk_id": "psy-001",
            "title": "动机访谈法 (MI) 四步法",
            "content": "倾听 — 澄清 — 唤起 — 承诺：以共情倾听学生现状，澄清其矛盾心理，唤起改变意愿，引导自主承诺。避免说教与对立，强调学生自主选择权。",
        },
        {
            "chunk_id": "psy-002",
            "title": "认知行为疗法 (CBT) 应对学业焦虑",
            "content": "识别自动化负性思维（如'我注定挂科'），用证据检验替代为现实评估；配合行为激活技术，将'通过课程'拆解为可量化每日小目标。",
        },
        {
            "chunk_id": "psy-003",
            "title": "共情倾听技术要点",
            "content": "1) 反映性倾听复述对方关键情绪 2) 不评判不打断 3) 开放式提问 4) 沉默允许。避免过早提建议、避免与其他学生比较。",
        },
    ],
    "policy": [
        {
            "chunk_id": "pol-001",
            "title": "校内困难补助等级标准",
            "content": "A 类 800 元/月（特困）、B 类 500 元/月（一般困难）、C 类 300 元/月（临时困难）。需提供家庭经济状况说明 + 院系班委评议 + 民主公示。",
        },
        {
            "chunk_id": "pol-002",
            "title": "学业警告与休学复学规则",
            "content": "连续两学期 GPA < 2.0 触发学业警告；可申请最长 2 学期休学，复学需通过院系学业能力评估并签署改进承诺书。",
        },
        {
            "chunk_id": "pol-003",
            "title": "勤工助学岗位申请规范",
            "content": "学生处每月发布岗位，单人不超过 60 小时/月，时薪不低于当地最低标准；优先困难学生 + 当前学业稳定学生（上学期 GPA ≥ 2.5）。",
        },
    ],
    "success": [
        {
            "chunk_id": "succ-001",
            "title": "高风险学生转化为科研典型",
            "content": "S 同学三大维度（学业/心理/经济）同时触发预警，经一学期综合干预后 GPA 升至 3.6 并参与院级科研立项，成为同级优秀典型。",
        },
        {
            "chunk_id": "succ-002",
            "title": "出勤异常学生转化为学习委员",
            "content": "Y 同学连续旷课 4 周，辅导员家访发现因家庭变故，启动心理疏导 + 灵活补考机制后，下学期当选学习委员并主持互助学习小组。",
        },
        {
            "chunk_id": "succ-003",
            "title": "心理预警学生成长为朋辈辅导员",
            "content": "Z 同学曾有抑郁倾向，经心理中心系统咨询 + 朋辈陪伴一年后，主动报名校朋辈辅导员培训项目，成为新生心理引导志愿者。",
        },
    ],
}


async def main() -> int:
    log.info("连接 Milvus %s:%s", settings.MILVUS_HOST, settings.MILVUS_PORT)
    connections.connect(host=settings.MILVUS_HOST, port=settings.MILVUS_PORT, timeout=10)

    embed = get_embedding_client()
    total = 0
    fallback_count = 0

    for source_key, items in SEED.items():
        col_name = settings.MILVUS_COLLECTIONS[source_key]
        if not utility.has_collection(col_name):
            log.error("集合 %s 不存在，请先运行 init_milvus", col_name)
            return 1

        col = Collection(col_name)
        chunk_ids, titles, contents, sources, vectors = [], [], [], [], []
        for item in items:
            vector = await embed.embed(item["content"])
            if all(x == 0.0 for x in vector):
                fallback_count += 1
                log.warning("chunk_id=%s 使用零向量降级", item["chunk_id"])
            chunk_ids.append(item["chunk_id"])
            titles.append(item["title"])
            contents.append(item["content"])
            sources.append(source_key)
            vectors.append(vector)

        col.insert([chunk_ids, titles, contents, sources, vectors])
        col.flush()
        total += len(items)
        log.info("集合 %s 写入 %d 条", col_name, len(items))

    await embed.close()
    log.info("完成：总计 %d 条，其中 %d 条使用零向量降级", total, fallback_count)
    if fallback_count > 0:
        log.warning("零向量降级会导致召回结果失真，建议部署真实 BGE Embedding 服务后重跑此脚本")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
