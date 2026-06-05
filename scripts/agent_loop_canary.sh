#!/usr/bin/env bash
# H-2.4：AgentLoop 灰度切流脚本（0 → 10% → 50% → 100%）。
#
# 作用：通过 Nacos 动态把 educare.agent.loop.{enabled,canary-percent} 推到 agent-canary.yml，
# 由 agent-service 的 @RefreshScope AgentLoopCanaryGate 热生效（无需重启）；每一档：
#   1) 推配置 → 等刷新
#   2) 对一组学生触发真实任务，轮询到终态，统计 COMPLETED/REJECTED/FAILED + 风险等级分布
#   3) 从 /actuator/prometheus 读 educare_agent_loop_routed_total 的 loop/legacy 增量，核对实际分流比例
#   4) 门控：本档出现 FAILED → 立即回滚（enabled=false）并退出 1；否则（默认手动）确认后进入下一档
#
# 切流前会先以 legacy（enabled=false）跑一遍同一组学生作为 baseline，供等级分布对比。
#
# 依赖：bash / curl / jq。Nacos 开启鉴权时用 NACOS_USER/NACOS_PASS 登录拿 accessToken。
#
# 用法：
#   bash scripts/agent_loop_canary.sh                       # 交互式逐档确认
#   AUTO=1 bash scripts/agent_loop_canary.sh                # 不停顿，自动放量（仅在已验证环境用）
#   STUDENT_IDS="1 2 3 4 5" STAGES="10 50 100" bash scripts/agent_loop_canary.sh
#   KEEP_ON_FINISH=1 bash scripts/agent_loop_canary.sh      # 跑完保留 100%（默认跑完回滚到 enabled=false）
#
# 关键参数（均可用环境变量覆盖）：
#   GATEWAY        触发/查询入口（经网关）           默认 http://localhost:8080
#   AGENT_DIRECT   actuator 直连（网关不转发 /actuator） 默认 http://localhost:8087
#   NACOS_ADDR     Nacos 地址                          默认 localhost:8848
#   NACOS_USER/NACOS_PASS  Nacos 账号                  默认 nacos/nacos
#   STUDENT_IDS    参与切流验证的学生 ID（空格分隔）   默认 "1"
#   STAGES         放量档位                            默认 "10 50 100"
#   TIMEOUT_SECONDS 单任务轮询超时                     默认 120
#   REFRESH_WAIT   推配置后等待热刷新秒数              默认 6
set -u

GATEWAY=${GATEWAY:-http://localhost:8080}
AGENT_DIRECT=${AGENT_DIRECT:-http://localhost:8087}
NACOS_ADDR=${NACOS_ADDR:-localhost:8848}
NACOS_USER=${NACOS_USER:-nacos}
NACOS_PASS=${NACOS_PASS:-nacos}
NACOS_GROUP=${NACOS_GROUP:-DEFAULT_GROUP}
NACOS_DATA_ID=${NACOS_DATA_ID:-agent-canary.yml}
STUDENT_IDS=${STUDENT_IDS:-1}
STAGES=${STAGES:-10 50 100}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-120}
INTERVAL=${INTERVAL:-2}
REFRESH_WAIT=${REFRESH_WAIT:-6}
AUTO=${AUTO:-0}
KEEP_ON_FINISH=${KEEP_ON_FINISH:-0}

require() { command -v "$1" >/dev/null 2>&1 || { echo "✗ 缺少命令: $1"; exit 1; }; }
require curl
require jq

NACOS_TOKEN=""

nacos_login() {
    # 鉴权开启时拿 accessToken；未开启鉴权时 login 可能 403/404，忽略即可（后续请求不带 token）
    local resp
    resp=$(curl -s -X POST "http://$NACOS_ADDR/nacos/v1/auth/login" \
        --data-urlencode "username=$NACOS_USER" --data-urlencode "password=$NACOS_PASS" 2>/dev/null)
    NACOS_TOKEN=$(echo "$resp" | jq -r '.accessToken // empty' 2>/dev/null)
    if [[ -n "$NACOS_TOKEN" ]]; then
        echo "  ✓ Nacos 登录成功（accessToken 已获取）"
    else
        echo "  ⚠ Nacos 未返回 accessToken（可能未开启鉴权），后续请求不带 token"
    fi
}

# 推送 agent-canary.yml：$1=enabled(true/false) $2=percent
nacos_publish() {
    local enabled=$1 percent=$2
    local content
    content=$(printf 'educare:\n  agent:\n    loop:\n      enabled: %s\n      canary-percent: %s\n' "$enabled" "$percent")
    local url="http://$NACOS_ADDR/nacos/v1/cs/configs"
    local args=(-s -X POST "$url"
        --data-urlencode "dataId=$NACOS_DATA_ID"
        --data-urlencode "group=$NACOS_GROUP"
        --data-urlencode "type=yaml"
        --data-urlencode "content=$content")
    [[ -n "$NACOS_TOKEN" ]] && args+=(--data-urlencode "accessToken=$NACOS_TOKEN")
    local out
    out=$(curl "${args[@]}")
    if [[ "$out" == "true" ]]; then
        echo "  ✓ Nacos 推送 $NACOS_DATA_ID → enabled=$enabled canary-percent=$percent"
    else
        echo "  ✗ Nacos 推送失败，响应: $out"
        return 1
    fi
}

# 读 educare_agent_loop_routed_total{path="loop|legacy"} 当前累计值，输出 "loop legacy"
read_routed() {
    local prom loop legacy
    prom=$(curl -s "$AGENT_DIRECT/actuator/prometheus" 2>/dev/null)
    loop=$(echo "$prom"   | awk -F' ' '/^educare_agent_loop_routed_total\{.*path="loop".*\}/   {print $2}' | head -1)
    legacy=$(echo "$prom" | awk -F' ' '/^educare_agent_loop_routed_total\{.*path="legacy".*\}/ {print $2}' | head -1)
    echo "${loop:-0} ${legacy:-0}"
}

# 触发单学生任务并轮询到终态，输出 "STATUS RISKLEVEL"
trigger_one() {
    local sid=$1 resp task_id status elapsed=0
    resp=$(curl -s -X POST "$GATEWAY/agent/api/v1/task/trigger/$sid")
    task_id=$(echo "$resp" | jq -r '.data // empty')
    if [[ -z "$task_id" || "$task_id" == "null" ]]; then
        echo "TRIGGER_FAIL -"
        return
    fi
    while [[ $elapsed -lt $TIMEOUT_SECONDS ]]; do
        local detail
        detail=$(curl -s "$GATEWAY/agent/api/v1/task/$task_id")
        status=$(echo "$detail" | jq -r '.data.status // empty')
        case "$status" in
            COMPLETED|REJECTED|FAILED)
                local risk
                risk=$(echo "$detail" | jq -r '.data.riskLevel // "-"')
                echo "$status $risk"
                return ;;
        esac
        sleep "$INTERVAL"
        elapsed=$((elapsed + INTERVAL))
    done
    echo "TIMEOUT -"
}

# 跑一组学生，回显逐条结果；通过全局数组回传统计
declare -A STAT
run_cohort() {
    local label=$1
    STAT[COMPLETED]=0; STAT[REJECTED]=0; STAT[FAILED]=0; STAT[TIMEOUT]=0; STAT[TRIGGER_FAIL]=0
    local r0 r1 before after
    before=$(read_routed)
    echo "  路由计数(前): loop/legacy = ${before// //}"
    for sid in $STUDENT_IDS; do
        local out st risk
        out=$(trigger_one "$sid")
        st=${out%% *}; risk=${out##* }
        STAT[$st]=$(( ${STAT[$st]:-0} + 1 ))
        printf "    [%s] student=%s → %-12s risk=%s\n" "$label" "$sid" "$st" "$risk"
    done
    after=$(read_routed)
    r0=$(( ${after%% *} - ${before%% *} ))
    r1=$(( ${after##* } - ${before##* } ))
    echo "  路由增量: AgentLoop=$r0  legacy=$r1"
    echo "  终态统计: COMPLETED=${STAT[COMPLETED]} REJECTED=${STAT[REJECTED]} FAILED=${STAT[FAILED]} TIMEOUT=${STAT[TIMEOUT]} TRIGGER_FAIL=${STAT[TRIGGER_FAIL]}"
}

confirm_or_abort() {
    [[ "$AUTO" == "1" ]] && return 0
    read -r -p "  → 本档通过，回车进入下一档（输入 n 中止并回滚）: " ans
    [[ "$ans" == "n" || "$ans" == "N" ]] && return 1
    return 0
}

rollback() {
    echo
    echo ">> 回滚：enabled=false（全量 legacy）"
    nacos_publish false 0 || echo "  ⚠ 回滚推送失败，请手动确认 Nacos $NACOS_DATA_ID"
}

abort() {
    echo
    echo "✗ $1"
    rollback
    exit 1
}

echo "============================================================"
echo " H-2.4 AgentLoop 灰度切流"
echo "  Gateway:     $GATEWAY"
echo "  Agent direct:$AGENT_DIRECT"
echo "  Nacos:       $NACOS_ADDR ($NACOS_DATA_ID@$NACOS_GROUP)"
echo "  Students:    $STUDENT_IDS"
echo "  Stages:      $STAGES"
echo "  Mode:        $([[ "$AUTO" == "1" ]] && echo 自动 || echo 逐档确认)"
echo "============================================================"

# 健康检查
HEALTH=$(curl -s "$AGENT_DIRECT/actuator/health" | jq -r '.status // empty' 2>/dev/null)
if [[ "$HEALTH" != "UP" ]]; then
    echo "⚠ agent-service /actuator/health = '${HEALTH:-无响应}'（继续，但 actuator 路由计数可能读不到）"
fi

echo
echo ">> 步骤 0：Nacos 登录"
nacos_login

echo
echo ">> 步骤 1：baseline（enabled=false，全 legacy）"
nacos_publish false 0 || abort "baseline 配置推送失败"
echo "  等待热刷新 ${REFRESH_WAIT}s..."; sleep "$REFRESH_WAIT"
run_cohort "baseline"
if [[ ${STAT[FAILED]} -gt 0 ]]; then
    abort "baseline 即出现 FAILED，链路本身有问题，先排查再切流"
fi
BASE_OK=$(( ${STAT[COMPLETED]} + ${STAT[REJECTED]} ))
echo "  baseline 健康任务数: $BASE_OK"

# 逐档放量
for pct in $STAGES; do
    echo
    echo ">> 阶段：canary-percent=$pct%（enabled=true）"
    nacos_publish true "$pct" || abort "阶段 $pct% 配置推送失败"
    echo "  等待热刷新 ${REFRESH_WAIT}s..."; sleep "$REFRESH_WAIT"
    run_cohort "canary-$pct"
    if [[ ${STAT[FAILED]} -gt 0 ]]; then
        abort "阶段 $pct% 出现 ${STAT[FAILED]} 个 FAILED，回滚"
    fi
    if [[ ${STAT[TIMEOUT]} -gt 0 || ${STAT[TRIGGER_FAIL]} -gt 0 ]]; then
        echo "  ⚠ 本档有 TIMEOUT/TRIGGER_FAIL，非 FAILED 但建议人工复核（可能是 MCP server / llama.cpp 未就绪）"
    fi
    if ! confirm_or_abort; then
        abort "人工中止于 $pct%"
    fi
done

echo
echo "============================================================"
echo "✓ 灰度全部通过：$STAGES"
if [[ "$KEEP_ON_FINISH" == "1" ]]; then
    echo "  保留 enabled=true canary-percent=100（KEEP_ON_FINISH=1）"
    nacos_publish true 100 || echo "  ⚠ 终态推送失败，请手动确认"
else
    echo "  按默认回滚到 enabled=false（如需全量上线请重跑加 KEEP_ON_FINISH=1，或手动置 100）"
    rollback
fi
echo "============================================================"
