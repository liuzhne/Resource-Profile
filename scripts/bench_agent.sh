#!/usr/bin/env bash
# EduCare Agent 简化压测（E-3）
#
# N 个学生并发 POST /agent/api/v1/task/trigger/{id}，
# 轮询到 COMPLETED/REJECTED/FAILED 后统计：
#   - 触发→终态的 wall-clock 耗时分布（min / P50 / P95 / P99 / max）
#   - 压测窗口内 Redis keyspace_hits / keyspace_misses 增量 → 命中率
#
# 用法：
#   bash scripts/bench_agent.sh                       # 默认 N=10
#   N=20 TIMEOUT_SECONDS=240 bash scripts/bench_agent.sh
#   STUDENT_IDS="1,2,3,4,5" bash scripts/bench_agent.sh
#   bash scripts/bench_agent.sh --n 30 --timeout 300
#
# 环境变量：
#   GATEWAY               default http://localhost:8080
#   N                     学生数量；当 STUDENT_IDS 留空时，从 /student/ids 取前 N 个
#   STUDENT_IDS           逗号分隔显式指定（覆盖 N）
#   TIMEOUT_SECONDS       总轮询超时，默认 240s
#   INTERVAL              轮询间隔，默认 1s
#   COMPOSE_FILE          docker-compose 路径（用于 Redis CLI 探测）
#                         default docker/docker-compose.yml
#   REDIS_HOST/REDIS_PORT 主机直连 redis-cli 时用，默认 localhost:6379
#
# 提示：
#   - agent-service 触发器有 30s 幂等窗口；同一 studentId 短时间内重复触发会复用任务。
#     选取的学生 id 越多越分散越能反映真实并发。
#   - 缓存命中率统计依赖 Redis INFO stats；无法访问时只展示耗时部分。

set -euo pipefail

GATEWAY=${GATEWAY:-http://localhost:8080}
N=${N:-10}
INTERVAL=${INTERVAL:-1}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-240}
COMPOSE_FILE=${COMPOSE_FILE:-docker/docker-compose.yml}
STUDENT_IDS=${STUDENT_IDS:-}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --n)        N=$2; shift 2 ;;
        --timeout)  TIMEOUT_SECONDS=$2; shift 2 ;;
        --interval) INTERVAL=$2; shift 2 ;;
        --gateway)  GATEWAY=$2; shift 2 ;;
        --ids)      STUDENT_IDS=$2; shift 2 ;;
        -h|--help)
            sed -n '2,28p' "$0"; exit 0 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

require() {
    command -v "$1" >/dev/null 2>&1 || { echo "✗ 缺少命令: $1"; exit 1; }
}
require curl
require jq
require python3

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }

run_redis_info() {
    if command -v redis-cli >/dev/null 2>&1; then
        redis-cli -h "${REDIS_HOST:-localhost}" -p "${REDIS_PORT:-6379}" INFO stats 2>/dev/null || true
    elif command -v docker >/dev/null 2>&1; then
        docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli INFO stats 2>/dev/null || true
    fi
}

extract_stat() {
    awk -F: -v key="$1" '$1==key{gsub(/\r/,"",$2);print $2;exit}'
}

echo "============================================================"
echo " EduCare Agent 简化压测"
echo "  Gateway:    $GATEWAY"
echo "  N:          $N"
echo "  Interval:   ${INTERVAL}s"
echo "  Timeout:    ${TIMEOUT_SECONDS}s"
echo "============================================================"

# ---------------- 0. 学生 id ----------------
echo
echo "[0/4] 准备学生 id 列表..."
if [[ -n "$STUDENT_IDS" ]]; then
    SELECTED_IDS=$(echo "$STUDENT_IDS" | tr ',' '\n' | sed '/^$/d')
    echo "  使用显式 STUDENT_IDS"
else
    RESP=$(curl -s "$GATEWAY/student/ids")
    ALL_IDS=$(echo "$RESP" | jq -r '.data[]?' 2>/dev/null || true)
    if [[ -z "$ALL_IDS" ]]; then
        echo "✗ 未拿到学生 id；响应: $RESP"
        echo "  可用 STUDENT_IDS=1,2,3 显式覆盖。"
        exit 1
    fi
    SELECTED_IDS=$(echo "$ALL_IDS" | head -n "$N")
fi
ACTUAL_N=$(echo "$SELECTED_IDS" | grep -c .)
echo "  ✓ 选取学生数: $ACTUAL_N"

# ---------------- 1. Redis 基线 ----------------
echo
echo "[1/4] 采集 Redis 基线..."
REDIS_BASE=$(run_redis_info)
H1=$(echo "$REDIS_BASE" | extract_stat keyspace_hits)
M1=$(echo "$REDIS_BASE" | extract_stat keyspace_misses)
if [[ -z "$H1" ]]; then
    echo "  ⚠ 无法访问 Redis（命中率统计将跳过）"
else
    echo "  baseline keyspace_hits=$H1  keyspace_misses=$M1"
fi

# ---------------- 2. 并发触发 ----------------
echo
echo "[2/4] 并发触发 $ACTUAL_N 个任务..."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/start" "$TMP/end" "$TMP/status"

trigger_one() {
    local sid=$1
    local t0
    t0=$(now_ms)
    local resp
    resp=$(curl -s -X POST "$GATEWAY/agent/api/v1/task/trigger/$sid" || echo '')
    local tid
    tid=$(echo "$resp" | jq -r '.data // empty' 2>/dev/null)
    if [[ -z "$tid" || "$tid" == "null" ]]; then
        echo "$sid $resp" >> "$TMP/triggers_failed.log"
        return
    fi
    # 仅记录该 taskId 首次出现的开始时间（防 30s 幂等导致同一 tid 写多次）
    if [[ ! -f "$TMP/start/$tid" ]]; then
        echo "$t0" > "$TMP/start/$tid"
    fi
    echo "$tid" >> "$TMP/triggers.log"
}

while IFS= read -r sid; do
    trigger_one "$sid" &
done <<<"$SELECTED_IDS"
wait

if [[ ! -s "$TMP/triggers.log" ]]; then
    echo "✗ 没有任何任务触发成功"
    [[ -s "$TMP/triggers_failed.log" ]] && head -3 "$TMP/triggers_failed.log"
    exit 1
fi

UNIQUE_TASKS=$(sort -u "$TMP/triggers.log")
UNIQUE_COUNT=$(echo "$UNIQUE_TASKS" | grep -c .)
TRIGGER_FAIL_COUNT=0
[[ -f "$TMP/triggers_failed.log" ]] && TRIGGER_FAIL_COUNT=$(grep -c . "$TMP/triggers_failed.log" || echo 0)

echo "  ✓ 触发成功: $((ACTUAL_N - TRIGGER_FAIL_COUNT))  失败: $TRIGGER_FAIL_COUNT"
echo "  ✓ unique taskId: $UNIQUE_COUNT  （幂等复用差额: $((ACTUAL_N - TRIGGER_FAIL_COUNT - UNIQUE_COUNT))）"

# ---------------- 3. 轮询 ----------------
echo
echo "[3/4] 轮询任务到终态..."
ELAPSED=0
while [[ $ELAPSED -lt $TIMEOUT_SECONDS ]]; do
    REMAIN=0
    while IFS= read -r tid; do
        [[ -f "$TMP/end/$tid" ]] && continue
        STATUS=$(curl -s "$GATEWAY/agent/api/v1/task/$tid" | jq -r '.data.status // empty' 2>/dev/null)
        case "$STATUS" in
            COMPLETED|REJECTED|FAILED)
                now_ms > "$TMP/end/$tid"
                echo "$STATUS" > "$TMP/status/$tid"
                ;;
            *) REMAIN=$((REMAIN+1)) ;;
        esac
    done <<<"$UNIQUE_TASKS"
    printf "  [%4ds] remaining=%d\n" "$ELAPSED" "$REMAIN"
    [[ $REMAIN -eq 0 ]] && break
    sleep "$INTERVAL"
    ELAPSED=$((ELAPSED+INTERVAL))
done

# ---------------- 4. Redis 末态 + 统计 ----------------
echo
echo "[4/4] 采集 Redis 末态 + 计算指标..."
REDIS_FINAL=$(run_redis_info)
H2=$(echo "$REDIS_FINAL" | extract_stat keyspace_hits)
M2=$(echo "$REDIS_FINAL" | extract_stat keyspace_misses)

DURATIONS=()
COMPLETED_N=0; REJECTED_N=0; FAILED_N=0; TIMEOUT_N=0
while IFS= read -r tid; do
    if [[ -f "$TMP/end/$tid" ]]; then
        t_end=$(cat "$TMP/end/$tid")
        t_start=$(cat "$TMP/start/$tid")
        DURATIONS+=("$((t_end - t_start))")
        case "$(cat "$TMP/status/$tid")" in
            COMPLETED) COMPLETED_N=$((COMPLETED_N+1)) ;;
            REJECTED)  REJECTED_N=$((REJECTED_N+1)) ;;
            FAILED)    FAILED_N=$((FAILED_N+1)) ;;
        esac
    else
        TIMEOUT_N=$((TIMEOUT_N+1))
    fi
done <<<"$UNIQUE_TASKS"

PCTL=$(python3 - "${DURATIONS[@]}" <<'PY'
import sys
xs = sorted(int(x) for x in sys.argv[1:])
n = len(xs)
def pct(p):
    if n == 0: return 0
    k = max(0, min(n - 1, int(round(p / 100.0 * (n - 1)))))
    return xs[k]
if n == 0:
    print("0 0 0 0 0 0 0")
else:
    avg = sum(xs) // n
    print(f"{n} {min(xs)} {pct(50)} {pct(95)} {pct(99)} {max(xs)} {avg}")
PY
)
read -r N_OK MIN P50 P95 P99 MAXV AVG <<<"$PCTL"

if [[ -n "$H1" && -n "$H2" ]]; then
    DH=$((H2 - H1))
    DM=$((M2 - M1))
    TOTAL=$((DH + DM))
    if (( TOTAL > 0 )); then
        RATE=$(python3 -c "print(f'{$DH / $TOTAL * 100:.1f}')")
    else
        RATE="N/A（窗口内 Redis 0 次访问）"
    fi
else
    DH="N/A"; DM="N/A"; RATE="N/A"
fi

cat <<EOF

============================================================
 压测结果
============================================================
触发学生数:        $ACTUAL_N
触发失败数:        $TRIGGER_FAIL_COUNT
unique 任务数:     $UNIQUE_COUNT
完成 / 拒绝 / 失败: $COMPLETED_N / $REJECTED_N / $FAILED_N
超时未完成:        $TIMEOUT_N

任务耗时 (ms, 触发→终态 wall-clock)：
  N    = $N_OK
  min  = $MIN
  avg  = $AVG
  P50  = $P50
  P95  = $P95
  P99  = $P99
  max  = $MAXV

Redis keyspace 命中（窗口内增量）：
  hits   Δ = $DH
  misses Δ = $DM
  命中率   = ${RATE}%
============================================================
EOF
