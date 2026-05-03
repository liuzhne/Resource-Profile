#!/usr/bin/env bash
# EduCare Agent 端到端冒烟测试
#
# 基础用法（依赖学生真实数据；NONE/LOW 风险会短路完成）：
#   bash scripts/smoke_test_agent.sh
#
# 强制完整 4 阶段流水线（用于联调验证 RAG/方案/审核）：
#   1) 启动 agent-service 时设置环境变量：
#        export EDUCARE_DEBUG_FORCE_RISK_LEVEL=high
#        cd backend && mvn -pl agent-service spring-boot:run
#   2) 运行此脚本即可走完 RISK→KNOWLEDGE→PLAN→AUDIT 全流程
#   3) 联调结束后 unset 该环境变量并重启服务
#
# 自定义参数：
#   GATEWAY=http://localhost:8080 STUDENT_ID=2 TIMEOUT_SECONDS=120 \
#   bash scripts/smoke_test_agent.sh
set -e

GATEWAY=${GATEWAY:-http://localhost:8080}
STUDENT_ID=${STUDENT_ID:-1}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-90}
INTERVAL=${INTERVAL:-2}

require() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "✗ 缺少命令: $1"
        exit 1
    }
}
require curl
require jq

echo "============================================================"
echo " EduCare Agent 端到端冒烟测试"
echo "  Gateway:    $GATEWAY"
echo "  StudentID:  $STUDENT_ID"
echo "  Timeout:    ${TIMEOUT_SECONDS}s"
echo "============================================================"

echo
echo "[1/3] 触发任务..."
TRIGGER_RESP=$(curl -s -X POST "$GATEWAY/agent/api/v1/task/trigger/$STUDENT_ID")
TASK_ID=$(echo "$TRIGGER_RESP" | jq -r '.data // empty')
if [[ -z "$TASK_ID" || "$TASK_ID" == "null" ]]; then
    echo "✗ 触发失败，响应: $TRIGGER_RESP"
    exit 1
fi
echo "  ✓ 任务 ID = $TASK_ID"

echo
echo "[2/3] 轮询状态..."
ELAPSED=0
FINAL_STATUS=""
while [[ $ELAPSED -lt $TIMEOUT_SECONDS ]]; do
    STATUS=$(curl -s "$GATEWAY/agent/api/v1/task/$TASK_ID" | jq -r '.data.status // empty')
    printf "  [%3ds] status=%s\n" "$ELAPSED" "$STATUS"
    case "$STATUS" in
        COMPLETED | REJECTED | FAILED)
            FINAL_STATUS=$STATUS
            break
            ;;
    esac
    sleep "$INTERVAL"
    ELAPSED=$((ELAPSED + INTERVAL))
done

if [[ -z "$FINAL_STATUS" ]]; then
    echo "  ✗ 超时未完成"
    exit 1
fi

echo
echo "[3/3] 阶段产物校验..."
DETAIL=$(curl -s "$GATEWAY/agent/api/v1/task/$TASK_ID")
echo "$DETAIL" | jq '{
    taskId: .data.id,
    studentId: .data.studentId,
    status: .data.status,
    riskLevel: .data.riskLevel,
    riskScore: .data.riskScore,
    riskType: .data.riskType,
    completedAt: .data.completedAt,
    stages: {
        risk:      (.data.riskAnalysisResult  != null and .data.riskAnalysisResult  != ""),
        knowledge: (.data.retrievedKnowledge  != null and .data.retrievedKnowledge  != ""),
        plan:      (.data.interventionPlan    != null and .data.interventionPlan    != ""),
        audit:     (.data.complianceAudit     != null and .data.complianceAudit     != "")
    }
}'

case "$FINAL_STATUS" in
    COMPLETED) echo; echo "✓ 端到端流程通过：状态 COMPLETED" ;;
    REJECTED)  echo; echo "⚠ 流程跑完但合规审核未通过（REJECTED） — 这也是预期分支之一" ;;
    FAILED)    echo; echo "✗ 任务执行失败（FAILED）"; exit 1 ;;
esac
