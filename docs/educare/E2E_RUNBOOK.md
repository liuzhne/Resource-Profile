# EduCare Agent 端到端真跑 Runbook（"可运行"证据采集）

> 目的：把"AgentLoop 真能跑通一个任务"从代码级证据(`AgentLoopE2ECodeTest`)升级为**活模型端到端证据**。
> 需要宿主有 llama.cpp（生成 LLM）+ Docker。本文件给出逐条命令与每步的"通过判据"。

## 0. 为什么需要本文件
代码级证据(无 LLM)已有：`AgentLoopE2ECodeTest` 用真实 AgentLoop + 脚本化 LLM 跑通
`think → 调 get_student_profile → final_answer → 解析 risk/plan/level` 全链路。
**唯一未覆盖的是"活的 14B 模型真的能稳定吐出 ReAct JSON + 双 JSON final_answer"**——这一步只能在有模型的机器上验证，即本 Runbook。

## 1. 前置：宿主起本地模型（llama.cpp / vLLM，OpenAI 兼容）
最少只需生成 LLM(8091)；要完整 RAG 还需 embedding(8092) + reranker(8093)。
```bash
# 生成 LLM（AgentLoop 必需）—— 端口 8091，模型如 qwen2.5-14b-instruct
~/edu-ai/start-llm-server.sh         # 或你的 llama.cpp server 命令，--port 8091
# RAG 用（可选）
bash scripts/start-embedding-server.sh   # 8092
bash scripts/start-reranker-server.sh    # 8093
```
通过判据：`curl http://localhost:8091/v1/models` 返回模型列表。

## 2. 一键起全栈（含 agent-service + 3 个 MCP server）
```bash
cd docker
docker compose up -d                       # 基础设施 + gateway + ai-inference + agent 全栈
docker compose ps                          # 等 agent-service / mcp-student-data / knowledge-rag-mcp / memory-mcp 变 healthy
```
通过判据：`docker compose ps` 中 4 个 agent 相关容器 `STATUS=healthy`。
> agent-service 配了 `depends_on: condition: service_healthy`，会等两个 MCP server 探活通过才启动。

## 3. 灌库（建表 + 种子）
```bash
# 主 schema 与扩展（含 mental_question 全字段、student_academic_record/attendance、intervention_feedback）
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/01_init.sql
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/04_student_extras.sql
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/05_intervention_feedback.sql
# RAG 知识入库（可选，hybrid/检索用）：POST /api/v1/rag/upsert，见 RAG_UPSERT_DESIGN.md
```

## 4. MCP server 冒烟（7 个 tool happy path）
```bash
bash scripts/mcp_smoke_test.sh             # 需 bash≥4 / curl / jq
```
通过判据：脚本对 student-data(8094) + knowledge-rag(8095) 共 7 个 tool 各调一次，末尾 exit 0。

## 5. 真跑 AgentLoop（核心证据）
开 AgentLoop（二选一）：
```bash
# 方式 A：直接给 agent-service 容器置环境变量并重启
EDUCARE_AGENT_LOOP_ENABLED=true docker compose up -d agent-service
# 方式 B：经 Nacos 灰度（@RefreshScope 热生效，无需重启）
bash scripts/agent_loop_canary.sh STUDENT_IDS="1" STAGES="100"
```
触发并观察：
```bash
TASK=$(curl -s -XPOST http://localhost:8080/agent/api/v1/task/trigger/1 | jq -r .data)
watch -n2 "curl -s http://localhost:8080/agent/api/v1/task/$TASK | jq '{status:.data.status, risk:.data.riskLevel}'"
curl -s http://localhost:8080/agent/api/v1/task/$TASK | jq '{status:.data.status, riskLevel:.data.riskLevel, risk:.data.riskAnalysisResult, plan:.data.interventionPlan}'
docker compose logs --tail=200 agent-service | grep -E "AgentLoop|iter=|COMPLETED|final_answer"
```
**"可运行"通过判据（全部满足才算真跑通）**：
1. 任务 `status` 走到 `COMPLETED`（或 `REJECTED`，合规分支）；
2. `riskAnalysisResult` 与 `interventionPlan` 两个字段非空且是合法 JSON（= final_answer 双 JSON 被解析落库）；
3. agent-service 日志出现 `[AgentLoop][task-..] iter=N COMPLETED`；
4. Langfuse(http://localhost:3001) 出现 `agent.loop` trace（需配 LANGFUSE_* key）。

## 6. 端到端业务冒烟（含注入/缓存/限流）
```bash
EDUCARE_DEBUG_FORCE_RISK_LEVEL=high bash scripts/smoke_test_agent.sh   # 强制走完整 4 阶段
```

## 7. agent vs legacy eval 对比（合格性证据，见第 5 件）
```bash
bash eval/agent_vs_legacy.sh           # 同一组学生分别按 legacy / agent 跑，比对风险等级一致性与对 ground truth 的准确率
```
通过判据：agent 路径对 ground truth 的等级一致率 **≥ legacy**（不回退）。

---
## 本环境为何没跑
本 Runbook 在交付时的开发沙箱中**无法执行**：无 Docker daemon、无 GPU、无 14B 模型权重、无任何 infra 端口在监听。
故"活模型端到端"留待具备上述条件的机器执行；代码级证据(`AgentLoopE2ECodeTest`)与一键编排(本 compose)已就绪，把执行成本降到"起模型 + 一条 compose"。
