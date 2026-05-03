"""
EduCare Agent 路由：风险识别 / 方案生成 / 合规审核
- /api/v1/agent/risk
- /api/v1/agent/plan
- /api/v1/agent/audit

设计原则：
1. 入参为通用 Dict[str, Any]，与 Java 端 Map<String,Object> 直通；
2. LLM 输出强制 JSON Schema，解析失败降级返回 fallback；
3. 不做业务校验，由 Java 编排层处理（Python 侧专注 LLM/RAG）。
"""
import logging
from typing import Any, Dict, List

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.json_parser import extract_json
from app.services.llm_client import get_llm_client

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/agent", tags=["agent"])


# ==================== Prompt 模板 ====================

RISK_PROMPT = """你是教育数据分析师，从学生画像识别学业风险。
仅基于事实推理，禁止臆测。涉及敏感标签时体现人文关怀，不作歧视性判定。

严格按以下 JSON 输出（不要任何解释）：
{
  "risk_level": "high|medium|low|none",
  "risk_score": 0-100 整数,
  "primary_risk_type": "学业滑坡|心理健康预警|出勤异常|经济困难影响|社交孤立|综合风险",
  "root_cause_analysis": "200字以内根因",
  "key_indicators": ["指标1: 数值", "指标2: 数值"],
  "recommended_intervention_types": ["学业辅导","心理疏导","经济援助","家校沟通"],
  "urgency_reason": "100字以内紧迫性说明"
}"""

PLAN_PROMPT = """你是学生工作干预方案专家。基于风险分析与检索到的同类案例/政策/心理学知识，
为该学生生成可执行的个性化干预方案。每个 immediate_action 必须可在 7 天内启动；
若使用了知识片段中的方法或政策，请在 references 字段引用对应 chunk_id。

严格按以下 JSON 输出：
{
  "report_title": "个性化干预方案 - 学生匿名ID",
  "summary": "一句话总结",
  "immediate_actions": [
    {"action": "...", "owner": "辅导员|班主任|心理中心", "deadline_days": 1-7, "references": ["chunk_id..."]}
  ],
  "long_term_plan": [
    {"phase": "1-2周|1月|学期", "goals": ["..."], "metrics": ["可量化的衡量指标"]}
  ],
  "talk_outline": "与学生面谈的 5 点提纲，注意倾听与共情",
  "resources": [
    {"type": "课程|讲座|心理咨询|经济资助", "name": "...", "link_or_contact": "..."}
  ],
  "references": ["所引用的 chunk_id 列表"]
}"""

AUDIT_PROMPT = """你是教育数据合规审核员，按以下维度审查干预方案：
1) 隐私保护：是否未泄露 PII；
2) 最小必要：建议是否仅围绕学业相关；
3) 教育伦理：是否避免歧视、标签化、家庭背景偏见；
4) 第三方风险：是否要求学生披露不当信息或与家庭外第三方接触。

严格按以下 JSON 输出：
{
  "audit_passed": true|false,
  "audit_items": [
    {"dimension": "privacy|necessity|ethics|third_party", "passed": true|false, "issue": "若不通过的具体说明"}
  ],
  "redacted_suggestions": ["针对未通过项的整改建议"],
  "manual_review_required": true|false
}"""


# ==================== 请求/响应模型 ====================

class RiskRequest(BaseModel):
    student_profile: Dict[str, Any] = Field(..., description="脱敏后的学生画像")


class PlanRequest(BaseModel):
    student_profile: Dict[str, Any]
    risk_analysis: Dict[str, Any]
    knowledge_chunks: List[Dict[str, Any]] = Field(default_factory=list)


class AuditRequest(BaseModel):
    student_profile: Dict[str, Any]
    intervention_plan: Dict[str, Any]


# ==================== 内部工具 ====================

async def _call_llm_json(system: str, user: str) -> Dict[str, Any]:
    llm = get_llm_client()
    try:
        response = await llm.ainvoke(
            [{"role": "system", "content": system}, {"role": "user", "content": user}]
        )
        return extract_json(response.content)
    except Exception as exc:
        logger.error("LLM 调用失败: %s", exc)
        return {}


# ==================== 路由 ====================

@router.post("/risk")
async def risk_analyze(req: RiskRequest) -> Dict[str, Any]:
    """阶段 1：风险识别。Java 端目前直连 ChatClient，此端点为容器化备选路径。"""
    import json as _json
    user = _json.dumps(req.student_profile, ensure_ascii=False)
    result = await _call_llm_json(RISK_PROMPT, user)
    if not result:
        return {
            "risk_level": "medium",
            "risk_score": 50,
            "primary_risk_type": "数据异常需人工复核",
            "root_cause_analysis": "LLM 暂不可用，默认中风险",
            "key_indicators": [],
            "recommended_intervention_types": ["家校沟通"],
            "urgency_reason": "建议人工复核",
        }
    return result


@router.post("/plan")
async def generate_plan(req: PlanRequest) -> Dict[str, Any]:
    """阶段 3：基于画像 + 风险分析 + 召回知识，生成可落地的干预方案。"""
    import json as _json
    user_payload = {
        "student_profile": req.student_profile,
        "risk_analysis": req.risk_analysis,
        "knowledge_chunks": req.knowledge_chunks,
    }
    user = _json.dumps(user_payload, ensure_ascii=False)
    result = await _call_llm_json(PLAN_PROMPT, user)
    if not result:
        return {
            "report_title": "干预方案 - 待人工补全",
            "summary": "LLM 暂不可用，转人工方案制定",
            "immediate_actions": [],
            "long_term_plan": [],
            "talk_outline": "请由辅导员主导",
            "resources": [],
            "references": [],
        }
    return result


@router.post("/audit")
async def compliance_audit(req: AuditRequest) -> Dict[str, Any]:
    """阶段 4：合规审核（隐私/最小必要/伦理/第三方）。"""
    import json as _json
    user_payload = {
        "student_profile_meta": {
            k: req.student_profile.get(k) for k in ("studentId", "grade", "major") if k in req.student_profile
        },
        "intervention_plan": req.intervention_plan,
    }
    user = _json.dumps(user_payload, ensure_ascii=False)
    result = await _call_llm_json(AUDIT_PROMPT, user)
    if not result:
        return {
            "audit_passed": False,
            "audit_items": [{"dimension": "system", "passed": False, "issue": "审核服务暂不可用"}],
            "redacted_suggestions": ["请人工审核此方案"],
            "manual_review_required": True,
        }
    return result
