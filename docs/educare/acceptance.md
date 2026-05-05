# EduCare 验收 Checklist

> 一份按阶段交付物组织的可执行核对清单。
> 操作顺序：先按 §1 跑通环境 → 再按 §2 一阶段一阶段勾。

---

## 1. 前置环境（必须先全部 ✅）

- [ ] Docker 基础设施（mysql / redis / nacos / milvus / etcd / minio）`docker compose ps` 全 healthy
- [ ] `agent_task` / `intervention_report` / `knowledge_citation` / `audit_log` 表存在  
      `docker exec edu-mysql mysql -uroot -proot -e "SHOW TABLES IN edu_portrait" | grep -E 'agent_task|intervention_report|knowledge_citation|audit_log'`
- [ ] Spring Boot 全 6 个服务（auth/user/student/mental/data/gateway）已注册到 Nacos
- [ ] `ai-inference-service:8090/health` 可访问
- [ ] 宿主 `localhost:8091/8092/8093` 全部监听（`lsof -iTCP:809{1,2,3} | grep LISTEN`）
- [ ] Milvus 4 集合 row_count > 0（attu http://localhost:8000）
- [ ] agent-service 已启动且 Nacos 显示 healthy

---

## 2. 阶段交付物核对

### A. 后端骨架 + DDL + Python 4 路由 + Java Feign 接通

- [ ] `sql/init/03_agent_init.sql` 包含 4 张表
- [ ] Python 路由：`/api/v1/agent/risk`、`/api/v1/rag/retrieve`、`/api/v1/agent/plan`、`/api/v1/agent/audit` 各返回 200 + JSON
- [ ] Java `AiInferenceClient` 4 个方法签名与上一致
- [ ] `agent-service` 通过 Feign 拉到 `student/{id}` 数据（不再 mock）

### B. Milvus 集合 + 种子数据 + 端到端冒烟

- [ ] `scripts/init_milvus.py` 幂等运行（再次运行无副作用）
- [ ] `scripts/seed_knowledge.py` 完成；输出 "完成：总计 12 条，其中 0 条使用零向量降级"
- [ ] `bash scripts/smoke_test_agent.sh` 通过：状态 COMPLETED 或 REJECTED（REJECTED 也算预期分支）

### C0. BGE Embedding/Reranker 走宿主 Metal

- [ ] `EMBEDDING_BASE_URL=http://host.docker.internal:8092/v1` 在 ai-inference 容器环境变量中
- [ ] `RERANKER_ENABLED=true`，`reranker_client.py` 调用 8093 不抛错
- [ ] `EDUCARE_DEBUG_FORCE_RISK_LEVEL=high` 可强制走完 4 阶段（验完取消）

### C. 前端预警中心 + 报告页

- [ ] `/agent/warning` 路由能展示任务列表，分页 / 状态 / 风险等级筛选可用
- [ ] 点击任务进入 `/agent/report/:id`，展示 4 阶段产物（风险 / 知识 / 方案 / 审核）
- [ ] `frontend/src/api/agent.js` 调用走 `/api` 代理到 gateway
- [ ] 没有暴露 PII 的字段（学号 / 姓名等已脱敏）

### D. 生产化

- [ ] 限流：连续 `for i in {1..20}; do curl -X POST .../trigger/1; done`，至少 1 次 HTTP 429
- [ ] 幂等：30s 内对同一 sid 5 次并发 trigger，`unique taskId ≤ 2`（smoke_test [D-3] 段已覆盖）
- [ ] Prompt 注入：smoke_test [D-1] 段中带 system: 越权指令，返回不出现"系统已被攻陷"等被诱导内容
- [ ] RAG 缓存：smoke_test [D-2] 段第二次响应耗时 < 第一次 / 3 或 < 50ms

### E. 验收与展示就绪

#### E-1 定时扫描调度
- [ ] `backend/agent-service/.../schedule/DailyScanScheduler.java` 存在
- [ ] `educare.schedule.enabled=false` 时（默认）服务启动后 02:00 不触发
- [ ] Nacos 改 `educare.schedule.enabled=true` 后下一次 cron 命中：日志含"开始定时扫描"且每个 sid 都有 trigger 调用
- [ ] 多实例并行：仅 1 个实例执行（另一实例日志含"已有实例持有定时扫描锁，跳过本次"）

测试用 cron（验证完改回 02:00）：
```yaml
educare.schedule.cron: "0 */1 * * * ?"   # 每分钟一次，验完恢复
```

#### E-2 RAG 检索评估
- [ ] `ai-inference-service/scripts/eval_retrieval.py` 存在
- [ ] `python -m scripts.eval_retrieval` 输出整体 + 4 集合分项 Recall@5 / MRR
- [ ] golden queries 数量 = 50；分布 case=13 / psychology=12 / policy=13 / success=12
- [ ] Recall@5 ≥ 0.85，MRR ≥ 0.70（模型与种子数据下的合理阈值；不达标则记录于失败明细表中讨论）
- [ ] `--output` 写出的 markdown 报告可读

#### E-3 简化压测
- [ ] `scripts/bench_agent.sh` 可执行（`-x` 位）
- [ ] `bash scripts/bench_agent.sh --n 10` 顺利结束并打印结果块
- [ ] 输出包含 N / min / avg / P50 / P95 / P99 / max
- [ ] Redis hits/misses Δ + 命中率（百分比）显示，第二次同参重跑命中率显著上升

#### E-4 项目文档
- [ ] `docs/educare/architecture.md` 存在，含拓扑表 / 时序图 / 状态机 / RAG 数据流
- [ ] `docs/educare/deploy.md` 存在，含先决条件 / 首次部署步骤 / Nacos 配置 / 故障排查
- [ ] `docs/educare/acceptance.md`（本文件）存在
- [ ] 三份文档相互引用链接可点开（GitHub / VS Code Markdown 预览均通过）

#### E-5 前端权限二次脱敏
- [ ] `frontend/src/directives/permission.js` 注册 `v-permission` 指令
- [ ] 心理原始数据相关 DOM 用 `v-permission="['psychologist','admin']"` 包裹
- [ ] 普通辅导员账号登录后这些字段从 DOM 中消失（不仅仅是 CSS 隐藏）

---

## 3. 端到端验证脚本（一次跑完）

```bash
set -e
cd /Users/derrick/Desktop/ccProject-rf/Resource-Profile/.claude/worktrees/cc-dev

# 冒烟（含 D 阶段验证）
GATEWAY=http://localhost:8080 STUDENT_ID=1 \
    bash scripts/smoke_test_agent.sh

# RAG 检索评估
docker compose -f docker/docker-compose.yml exec -T ai-inference-service \
    python -m scripts.eval_retrieval

# 压测
bash scripts/bench_agent.sh --n 10 --timeout 240

# 数据校验
docker exec edu-mysql mysql -uroot -proot -e "
SELECT status, COUNT(*) FROM edu_portrait.agent_task GROUP BY status;
SELECT COUNT(*) FROM edu_portrait.intervention_report;
SELECT COUNT(*) FROM edu_portrait.audit_log;"
```

任一步骤失败即未达标，请按 [`deploy.md` §5 故障排查](./deploy.md#5-故障排查) 处理。

---

## 4. 演示对外故事线（可对照路演 PPT）

1. **开场**：架构图（`architecture.md §1`）→ 强调 Java/Python 异构、本地 LLM、零出域。
2. **触发一次预警**：前端预警中心 → 选学生 → "立即分析" → 进度条逐阶段亮起。
3. **打开报告**：4 阶段产物可视化；指引"知识引用"区显示 RAG 召回的 chunk 来源。
4. **生产化加固**：用 `bench_agent.sh` 实时演示 P99 + 缓存命中率上升。
5. **定时扫描**：在 Nacos 切换 `educare.schedule.enabled=true`，等下一次 cron 命中显示批量任务涌入。
6. **风险案例**：演示 REJECTED 路径——合规 LLM 拒绝 → 转人工。

通过本 checklist 全部 ✅ 即代表 EduCare 子系统验收通过。
