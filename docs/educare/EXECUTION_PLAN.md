# EduCare 改进落实计划（Execution Playbook）

> **作用**：将 [`IMPROVEMENT_2026_MAY.md`](./IMPROVEMENT_2026_MAY.md) 的 Phase G/H/I 拆为可逐步执行的原子任务清单。**每次开工前先读本文件 → 找到第一个未完成项 → 执行 → 回写本文件勾选与备注**。
>
> **设计源**：`IMPROVEMENT_2026_MAY.md`（v1.1，2026-05-12 拍板）
> **创建日期**：2026-05-13
> **最近更新**：2026-05-21（H-2.1 完成：新建包 `com.edu.agent.core/`，落 `AgentLoop` think→tool→observe 循环骨架（ReAct JSON 协议，非 Spring AI native tool calling）+ 4 个数据 record / 1 个 status enum + 3-case Mockito + JUnit 5 单元测试；不动旧 4 阶段、不引 MCP client、不改 yml；agent-service `pom.xml` 加 `spring-boot-starter-test` scope=test。指针推进至 H-2.2 在 `AgentLoop` 上接入 MCP client + G-1 prompt caching + G-5 Langfuse trace）

---

## 0. 使用约定（Update Protocol）

每次执行流程：

1. **读** 本文件，跳到 §1 "下一步指针"，确认要做什么
2. **执行** 该原子任务（可能跨多个文件 / 多次 tool 调用）
3. **回写** 本文件：
   - 把该任务前的 `[ ]` 改为 `[x]`
   - 在该任务下方追加一行 `- 完成于 YYYY-MM-DD：<一句话备注，含关键 commit 或文件路径>`
   - 更新 §1 的 "下一步指针" 为新的第一个未完成项
   - 顶部 "最近更新" 改为今日日期 + 一句变更摘要
4. **不要** 在本文件里写实现细节、代码片段或长篇分析 —— 那些放进 commit message 或 PR 描述
5. 重大决策变更（如某子任务被拆分 / 合并 / 砍掉）→ 在文件底部 §6 "变更记录" 追加一行说明，再改任务列表

任务粒度原则：每条 `[ ]` 应是 **0.5-2 天可完成 + 单次 PR 可合并** 的尺度。如果发现某条远超此粒度，先拆分再开工。

---

## 1. 下一步指针（Next Action）

**当前阶段**：Phase H（MCP 化 + Agent Loop 重构）
**下一步**：→ §3 H-2.2 在 `AgentLoop` 上接入 G-1 prompt caching + G-5 Langfuse trace（含给 `spring-ai-starter-mcp-client-webmvc` 加依赖 + `application.yml` 写 `spring.ai.mcp.client.streamable-http.connections.{student-data,knowledge-rag}`，把 `ToolCallbackProvider` 注入 AgentLoop 调用方）
- **H-1 子阶段全部完结**：H-1.1（选型）+ H-1.1.5（Spring AI 1.0.0 GA）+ H-1.2（student-data MCP server，8094）+ H-1.1.6（Spring AI 1.1.6 GA + MCP transport 全栈 Streamable HTTP）+ H-1.3（FastMCP knowledge-rag MCP server，8095）+ H-1.4（`mcp_smoke_test.sh` 一键回归脚本）均已合入
- **H-2.1 完结**：`backend/agent-service/src/main/java/com/edu/agent/core/` 5 个 Java 源（`AgentLoop` + `AgentLoopRequest/Result/Status/AgentTrace`）+ `AgentLoopTest` 3 case 全绿；ReAct JSON 协议跑通，控制流可单测、可 trace、可早停
- **H-1.2 / H-1.3 用户侧 smoke 一键化**：两个 server 起好后执行 `bash scripts/mcp_smoke_test.sh`（依赖 bash≥4 / curl / jq）即可完成 7 个 tool 的 happy path 回归，取代手动 mcp-inspector 流程
- **Phase G 全部完成**（代码侧）；只剩用户侧 `FIELD_PERMISSION_VERIFY.md`（G-2.3）实跑回写
- **smoke 剩余 3 项待用户实跑**：本地 llama.cpp 起后跑一次 `/agent/api/v1/task/trigger/{id}`，验证 `LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor` + Langfuse trace 推送（见 `MCP_DESIGN.md §2` 项 2-4）

---

## 2. Phase G —— 快速赢 + 基础设施（第 1-2 周）

### G-1 Prompt Caching（llama.cpp slot cache 路线）

- [x] **G-1.1** 盘点 4 个 LLM 调用点
  - 完成于 2026-05-12：见 IMPROVEMENT 文档；4 点为 Java `RiskAnalyzeService.java:73-77` + Python `agent.py` 的 risk/plan/audit 三处
- [x] **G-1.2** 锁定 llama.cpp slot cache 路线（不走 Spring AI 1.1+ cache_control）
  - 完成于 2026-05-12：决策记录在 IMPROVEMENT §0
- [x] **G-1.3** 抽离 prompts 到独立资源文件（保证字节稳定）
  - 完成于 2026-05-12：Python 侧已落到 `ai-inference-service/app/prompts/{risk,plan,audit}.system.md`；Java 侧 `RiskAnalyzeService.java:24-28` 已附 byte-stability 注释
- [x] **G-1.4** 透传 `cache_prompt:true` 给 llama.cpp（双侧）
  - 完成于 2026-05-13：
    - Python：新增 `chat_completion_raw` (httpx) 走 `/v1/chat/completions`，body 注入 `cache_prompt:true`；`agent.py:_call_llm_json` 改用此函数（`llm_client.py` + `agent.py`）
    - Java：新增 `LlamaCppCachePromptInterceptor`（拦截 `/chat/completions` 路径，向 JSON body 注入 `cache_prompt:true`），通过自定义 `RestClient.Builder` 挂到 `OpenAiApi` bean（`SpringAiConfig.java` + `LlamaCppCachePromptInterceptor.java`）
    - 副带修复：`AgentTaskServiceImpl.java:311/336` 预先存在的 Long→String 编译错（mirror line 272 的 `String.valueOf` 模式），否则 mvn compile 阻塞
- [x] **G-1.5** 拦截 LLM 响应，抓 `timings.cached_n / prompt_n`，本地累加 metrics
  - Python ✅ 完成于 2026-05-13：与 G-1.4 耦合实现 —— `chat_completion_raw` 拿到原始 JSON 后自动调 `record_llm_response(route, data)`，按 risk/plan/audit 三路分别统计
  - Java ✅ 完成于 2026-05-13：新增 `LlmMetricsInterceptor`（`ClientHttpRequestInterceptor`），response 侧拿 buffered body，抓 `timings.{prompt_n, cached_n, predicted_n, prompt_ms}` 喂 5 个 Micrometer meter（`educare.llm.{prompt_tokens, cached_tokens, completion_tokens, calls}` counter + `educare.llm.prefill` timer）；通过 `SpringAiConfig.openAiApi(LlmMetricsInterceptor)` 注入；`cache_hit_rate` 不写 gauge，留 Prom 端 PromQL 算
- [x] **G-1.6** 暴露 `/api/v1/diagnostics/llm-metrics` 端点 + 验收脚本
  - Python ✅ 完成于 2026-05-13：新增 `app/api/diagnostics.py` 注册到 `main.py`；脚本 `scripts/verify_prompt_cache.sh`（连发两次同 payload，看增量 `cached_tokens / prompt_tokens ≥ THRESHOLD`，默认 0.8）；手册 `docs/educare/PROMPT_CACHE_VERIFY.md`
  - Java ✅ 完成于 2026-05-13：新增 `DiagnosticsController` 路径 `/agent/api/v1/diagnostics/llm-metrics`，从 `MeterRegistry` 读 6 个数（calls / prompt / cached / completion / hit_rate / prefill 累计）；Prom 全量 exposition 仍走 `/actuator/prometheus`

### G-2 后端字段级权限

- [x] **G-2.1** 设计字段权限模型（角色 → 字段白名单），输出 `docs/educare/FIELD_PERMISSION.md`
  - 完成于 2026-05-13：覆盖 6 个角色 × 4 档分级（PUBLIC/MEDIUM/HIGH/EXTREME）矩阵 + 4 个 entity 字段总表；落地方案选 `@SensitiveField` 注解 + `ResponseBodyAdvice`；拆出 G-2.2 五个子步 a-e；行级权限与 audit_log 明确推迟 Phase I-4
- [ ] **G-2.2** 按 `FIELD_PERMISSION.md §6` 落地实施
  - [x] **G-2.2-a** `common/.../security/`：`SensitiveField` 注解 + `Sensitivity` 枚举 + `FieldPermissionAdvice`（`@RestControllerAdvice` 实现 `ResponseBodyAdvice`）+ 反射缓存
    - 完成于 2026-05-13：4 个新文件（Sensitivity / SensitiveField / RequestContext / FieldPermissionAdvice）；advice 用 `@ConditionalOnProperty(educare.field-permission.enabled=true)` 默认关闭，避免链路未补齐时误伤；矩阵实现见 `FieldPermissionAdvice.canSee`，与 `FIELD_PERMISSION.md §4` 列级部分一致；`mvn -pl common -am compile` 通过
  - [x] **G-2.2-b** JWT 加 `roles: List<String>` claim：`auth-service/.../AuthServiceImpl.java` 登录时查 `sys_role` 写入；`common/.../JwtUtil.java` 加 `parseRoles()`
    - 完成于 2026-05-13：抽 `buildClaims(User)` 复用于 login + refreshToken（防刷新后角色丢失）；`JwtUtil.parseRoles()` 缺失/异常一律返回空 `Set`（调用方按无角色处理）；`mvn -pl auth-service,common -am compile` 通过
  - [x] **G-2.2-c** `RoleContextFilter`（`common` 模块，`OncePerRequestFilter`），把 JWT roles 解到 `RequestContext`
    - 完成于 2026-05-13：`Bearer` 头解析 → `JwtUtil.parseRoles` → `RequestContext.setRoles`，`finally` 清 ThreadLocal 防线程复用串角色；非 Bearer / 空 / 非法 token 一律不灌（advice 走"无角色"降级）；不做 auth 决策（拦截由 auth-service 链路负责）；类未加 `@Component`，由 G-2.2-e 用 `FilterRegistrationBean` 注册
  - [x] **G-2.2-d** 给 4 个 entity（Student / Teacher / MentalAssessment / User）按 `FIELD_PERMISSION.md §3` 加 `@SensitiveField` 注解；User.password 用 `@JsonIgnore` 永不返回
    - 完成于 2026-05-13：Student（birthDate=HIGH, gpa/credits=MEDIUM）；Teacher（birthDate=HIGH, education=MEDIUM）；MentalAssessment（score/result/suggestion=EXTREME, level=MEDIUM）；User × 2（auth-service + user-service 各一份，password 加 `@JsonIgnore`，email/phone=HIGH）；4/5 模块 `mvn compile` 通过，mental-service 因预先存在 `Question` 实体字段缺失导致编译断（与本任务无关，见 §8）；`gender` 等未列出字段不标，默认 PUBLIC，linter 上线后再补
  - [x] **G-2.2-e** `common` 模块加 `@AutoConfiguration`，所有 service 自动启用 advice 与 filter
    - 完成于 2026-05-13：新增 `FieldPermissionAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnProperty(educare.field-permission.enabled=true)`）；3 个 `@Bean`：advice / filter / `FilterRegistrationBean<RoleContextFilter>`（url `/*`，order `LOWEST_PRECEDENCE - 100`，让 auth filter 先跑）；登记到 `META-INF/spring/...AutoConfiguration.imports` 第 4 行；`mvn -pl common,auth-service,student-service,teacher-service,user-service -am compile` 通过
- [~] **G-2.3** 前端去除 "假脱敏"（信任后端返回），手动测试 admin / teacher / counselor / academic_advisor / student 五角色对同一 studentId 详情的字段集 diff，与 `FIELD_PERMISSION.md §4` 矩阵对齐
  - 代码部分完成于 2026-05-13：`frontend/src/directives/permission.js` JSDoc 改写为 "UX-only，非安全防线"（G-2.2 后后端 advice 是权威）；新增 `docs/educare/FIELD_PERMISSION_VERIFY.md`（启用配置、5 角色测试账号、3 接口期望矩阵、一键 curl+jq 脚本、5 类故障排查表、通过标准 checklist）
  - 手测部分 ⏳ 待用户运行 `FIELD_PERMISSION_VERIFY.md §4` 脚本；通过后回写勾选 + 顶部加"验收通过"

### G-3 启用 E-1 定时扫描 + Prometheus 监控

- [x] **G-3.1** 在 `agent-service/pom.xml` 加 `micrometer-registry-prometheus` 依赖 + `actuator` 暴露 `/actuator/prometheus`
  - 完成于 2026-05-13：pom 加 `spring-boot-starter-actuator` + `io.micrometer:micrometer-registry-prometheus`；`application.yml` 加 `management.endpoints.web.exposure.include: health,info,prometheus` + `management.metrics.tags.application=${spring.application.name}`；jar 已解析（micrometer 1.12.5 / actuator 3.2.5）；`mvn -pl agent-service -am compile` 通过
- [x] **G-3.2** 给 `DailyScanScheduler` 加 Micrometer counter / timer（trigger 总数、失败数、单次耗时）
  - 完成于 2026-05-13：注入 `MeterRegistry`，`@PostConstruct` 预创建 3 个 meter（`educare.daily_scan.triggered` / `educare.daily_scan.failed` 两个 Counter + `educare.daily_scan.duration` 一个 Timer）；scanAll 循环里同步 `counter.increment()`，finally 块用 `scanTimer.record(elapsedMs, MILLISECONDS)`；指标名走点号分隔，Prometheus exposition 时自动转下划线带 `_total/_seconds` 后缀；`mvn -pl agent-service -am compile` 通过
- [x] **G-3.3** 把 G-1.5 的 LLM metrics 也接 Micrometer（cache_hit_rate gauge、tokens_total counter）
  - 完成于 2026-05-13：随 G-1.5 Java 一同实现 —— `LlmMetricsInterceptor` 已是 Micrometer 端，所有 LLM 调用经 Spring AI 时自动累加；Python 侧已有 `llm_metrics.snapshot()` 透出，未来 G-5 Langfuse 接入后这层指标合并到 trace 维度
- [x] **G-3.4** Nacos 里把 `educare.schedule.enabled` 在测试环境置 true，本地拉一次跑通
  - 验收手册完成于 2026-05-13：`docs/educare/SCHEDULE_METRICS_VERIFY.md` —— 6 节启动/配置/触发/验证流程 + 6 类故障排查表 + 后续 Prometheus/Grafana 接入建议；推荐用 `EDUCARE_SCHEDULE_CRON="0 */1 * * * ?"` 把 cron 改为每分钟避免等到 02:00
  - 实跑验证 ⏳ 待用户在本地启 mysql/redis/nacos + agent-service 后执行手册

### G-4 增量知识导入 API

- [x] **G-4.1** 设计 `/api/v1/rag/upsert`（Python 侧）的 schema：`{collection, doc_id, text, metadata, chunk_strategy}`
  - 完成于 2026-05-13：`docs/educare/RAG_UPSERT_DESIGN.md` 10 节 —— Pydantic 请求/响应 schema、Milvus 字段映射决策（不改 schema，用 `chunk_id={doc_id}_{idx:04d}` 命名约定承担分组）、3 种 chunk 策略（none/fixed_size/sentence）、delete-then-insert 幂等流程（解释为何不用 pymilvus.upsert）、`X-Admin-Token` 鉴权 + doc_id+text-hash 短路、字段名/路径/状态码约定、已知限制（metadata 只 title/source 入库）
- [x] **G-4.2** 实现：BGE 嵌入 → Milvus upsert（已有 collection 走 update，新 doc 走 insert）
  - 完成于 2026-05-13：3 个新/扩文件 ——
    1. `app/services/text_splitter.py`（none/fixed_size/sentence 三策略 + `MAX_CHUNKS=200` 上限）
    2. `app/services/milvus_client.py` 扩 `delete_by_doc_id(collection, doc_id)` 走 `chunk_id like "{doc_id}_%"` + `insert_chunks(collection, rows)` 批量插入并 flush
    3. `app/api/rag_upsert.py` 路由 `POST /api/v1/rag/upsert`：validate → split → 顺序 embed N 次（带 24h emb 缓存）→ delete-then-insert → 返回 UpsertResponse 含 chunks_written / deleted_first / embedding_ms / milvus_ms；422/503 错误码按设计文档 §8
  - `main.py` 注册 router；`ast.parse + 3 个 splitter 行为断言` 全过；鉴权 + 幂等短路留 G-4.3
- [x] **G-4.3** 加 admin token 鉴权 + 幂等键（doc_id 去重）
  - 完成于 2026-05-13：
    - `app/core/config.py` 加 `ADMIN_TOKEN`（env `EDUCARE_ADMIN_TOKEN`，默认空）+ `UPSERT_HASH_TTL`（默认 7d）
    - `rag_upsert.py` `_check_admin_token` 走 `hmac.compare_digest` 防时序攻击；ADMIN_TOKEN 未配置时 503（fail-closed），不匹配 401
    - 幂等：`sha256(text)[:32]` 存 Redis key `edu:rag:upsert:hash:{collection}:{doc_id}`；命中直接 `UpsertResponse(skipped=true, ...=0)`；未命中走完流程后 `cache_setex` 写入
    - `tests/test_text_splitter.py` 14 个 `unittest` 用例覆盖 4 类（none/fixed_size/sentence/unknown）+ 常量；`python -m unittest tests.test_text_splitter` 全过
    - **G-4 段全部完成**

### G-5 Langfuse 接入

- [x] **G-5.1** Docker compose 加 Langfuse self-hosted（postgres + langfuse-server），或确认走云端实例（决策点）
  - 完成于 2026-05-14：决策选 self-hosted（已有 compose 编排 + traces 数据非敏感无需云端隔离 + 免 API key/付费层）；用 v2（2 容器）而非 v3（5 容器，clickhouse + redis + worker）；compose 加 `langfuse-postgres`（pg15-alpine）+ `langfuse-server`（langfuse/langfuse:2，3000 → 宿主 3001 避免与 attu 冲突）+ `langfuse_pg_data` 卷；用 `profiles: [langfuse]` 默认不启（resume 场景不浪费），按需 `docker-compose --profile langfuse up -d`；env 占位用 `${LANGFUSE_DB_PASSWORD:-langfuse123}` 等默认，生产必须改
- [x] **G-5.2** Python 侧：`requirements.txt` 加 `langfuse`，在 `_call_llm_json` 包一层 `@observe`
  - 完成于 2026-05-14：`requirements.txt` 加 `langfuse>=2.36,<3.0`（v2 SDK 对应 v2 server）；`config.py` 加 `LANGFUSE_PUBLIC_KEY/SECRET_KEY/HOST` 三个 env；`llm_client.py` 用 try-import + no-op fallback 降级（SDK 缺失或 keys 为空时透明无影响）；`chat_completion_raw` 加 `@observe(as_type="generation")` 装饰器，调 `langfuse_context.update_current_observation` 两次：调用前打 `name=llm.{route}`/model/input/metadata，调用后透 llama.cpp `timings.{prompt_n,predicted_n,cached_n,prompt_ms}` 到 `usage` + `metadata`；`ast.parse + import` 在无 langfuse 包环境下通过
- [x] **G-5.3** Java 侧：`pom.xml` 加 `langfuse-java`（若无则用 OTel exporter），在 ChatClient 拦截器埋 trace
  - 完成于 2026-05-14：决策 **不引入 unofficial Java SDK 也不走 OTel**（Langfuse v2 不支持 OTel），直接 HTTP POST `/api/public/ingestion`：
    - `application.yml` 加 `langfuse.{public-key,secret-key,host}` env passthrough
    - 新 `LangfuseClient.java`：`@PostConstruct` 检 keys 三者全配才 `enabled`，否则全部 trace 调用静默 no-op；`traceGeneration(...)` 走 `@Async("agentExecutor")`（复用 G 已有的执行器）批 POST 一条 `trace-create` + 一条 `generation-create`，HTTP Basic 鉴权 `public:secret`
    - 扩 `LlmMetricsInterceptor`：从 request body 抽 `model + messages` + 从 response 抽 `output + timings`，调 `langfuseClient.traceGeneration` 异步上报；任何解析异常仅 debug 日志
    - `mvn -pl agent-service -am compile -q` 通过
- [x] **G-5.4** 前端 "管理员追踪" 页 iframe 嵌 Langfuse（路由在 `frontend/src/router/index.js`）
  - 完成于 2026-05-14：新增 `frontend/src/views/admin/trace.vue`（满高 iframe + 刷新/新窗口动作 + 未配置 URL 时友好 empty 占位）；router `/admin/trace` 加在 `Admin` 子节点（继承父 `roles: ['admin']`，title 'LLM 追踪'，icon 'Connection'）；`frontend/.env.example` 落 `VITE_LANGFUSE_URL=http://localhost:3001` 模板；iframe `sandbox="allow-same-origin allow-scripts allow-forms allow-popups"` 允许 Langfuse SPA 必需特性

### G-6 Braintrust eval 集启动构建（先 50 例）

- [x] **G-6.1** 决策：Braintrust SaaS vs 自建 promptfoo（决策点）
  - 完成于 2026-05-14：选 **promptfoo**。三条理由：(a) resume 项目无付费 tier 预算；(b) YAML 配置 + JSONL 数据集随代码 git 版本化，对齐 "Eval 驱动迭代" 原则；(c) Langfuse（G-5）已覆盖**在线 trace**，离线 eval 走轻量 CLI 即可，不需要再付费拿一个 SaaS dashboard。运行方式留 G-6.4 README 详写
- [x] **G-6.2** 在 `eval/` 目录建 `risk_assessment.jsonl` 50 例（input: 学生画像快照；expected: 风险等级 + 理由要点）
  - 完成于 2026-05-14：`eval/risk_assessment.jsonl` 50 条；每条含 `id` / `description` / `input.student_profile`（覆盖 GPA / failedCourses / attendanceRate / mentalHealthLevel / familyEconomicLevel / counselorNotes 等 7-10 个字段）/ `expected.{risk_level, primary_type_hints, key_phrases}`；分布：等级 none=8 / low=12 / medium=17 / high=13；类型覆盖 学业 18 / 心理 29 / 经济 8 / 社交 7（部分多打标签）；边缘 2 例（RA-049 数据缺失、RA-050 高 GPA + 重度量表矛盾信号）；JSON 行解析全过
- [x] **G-6.3** 写一个 `eval/run_eval.py` 跑现有 risk endpoint 并打分（faithfulness / 等级一致率）
  - 完成于 2026-05-14：异步 `httpx + asyncio.Semaphore` 并发跑（默认 4），三指标：等级一致率（exact）/ 等级加权分（相邻 0.5）/ 关键短语命中率（substring 大小写不敏感）；输出 JSON 全量 + 可选 MD 报告 + 控制台进度；混淆矩阵 + 失败用例列表；CI 退出码（exact ≥ 0.6 → 0，否则 1）；httpx 走 lazy import，纯函数（`_level_score / _phrase_hits / _flatten_response`）在无 httpx 环境也能验证；用例：`python eval/run_eval.py --base-url http://localhost:8090 --md eval/run_results.md`
- [x] **G-6.4** README 说明运行方式；CI 接入留到 H-6
  - 完成于 2026-05-14：`eval/README.md` 8 节 —— 快速开始（一行命令）、文件清单、JSONL schema（每字段语义）、4 个指标定义 + 计算公式、CI 接入草案（GitHub Actions YAML + 阈值演进路径 0.6 起步）、增量维护规则（等级分布约定、改 ground truth 的 PR 要求）、5 类已知限制（覆盖度/RAGAS 替代/三 endpoint 单点/多标签未启/温度未锁）、与 Langfuse 在线 trace 的分工说明；**Phase G 代码部分全部完成**

**Phase G 验收总标准**：
- Langfuse 看到完整 trace（含 tool 调用、token 数、cache 状态）
- `/agent/api/v1/diagnostics/llm-metrics` 返回 cache_hit_rate ≥ 50%（同学生重复触发场景下 ≥ 80%）
- E-1 调度器在测试环境每日跑通，Prometheus 抓到指标
- 字段权限通过角色矩阵手测
- Eval 集 ≥ 50 例可本地跑通

---

## 3. Phase H —— MCP 化 + Agent Loop 重构（第 3-6 周，瘦身版）

> 瘦身决策（IMPROVEMENT §0）：仅 student-data + knowledge-rag 2 个 MCP Server + 主 Agent Loop + 4 个 Skill + Model Router（本地/云端双路由）。H-5/H-6 视进度。

### H-1 MCP Servers（瘦身：仅 2 个）

- [x] **H-1.1** 选型确认：Spring AI MCP starter 版本与 stdio/SSE 传输模式
  - 完成于 2026-05-14：`docs/educare/MCP_DESIGN.md` 9 节决策 ——
    - Spring AI **升 1.0.0-M6 → 1.0.0 GA**（M6 无 MCP starter；GA 起有 `spring-ai-mcp-{server,client}-spring-boot-starter`）；升级 smoke checklist 4 项落在文档 §2
    - 传输模式 **统一 SSE / streamable HTTP**，拒绝混用 stdio：跨语言 + docker-compose 友好；唯一退路是性能不达标时改 stdio
    - SDK：Java 用 spring-ai 官方 starter，Python 用 `fastmcp>=0.4`
    - 端口规划：student-data=8094 / knowledge-rag=8095；agent-service application.yml 配置草案见 §4
    - 7 个 tool 命名约定（snake_case + typed args + JSON-serializable return）见 §5
    - 4 类风险与折中（API 漂移 / SDK patch / Python 依赖冲突 / spec 版本演进）见 §7
    - 明确推后：MCP 鉴权 / sandboxing / memory-server（瘦身后视进度）
- [x] **H-1.1.5** Spring AI `1.0.0-M6 → 1.0.0` GA 升级（单独 PR，H-1.2 前置）
  - 完成于 2026-05-19：5 处改动 ——
    1. `backend/pom.xml`：`spring-ai.version` 改 `1.0.0`；移除 `spring-milestones` 仓库声明（GA 已上 Maven Central）
    2. `backend/agent-service/pom.xml`：starter artifactId 改 `spring-ai-starter-model-openai`（GA 命名规约 `spring-ai-starter-model-{model}`），删去显式 `spring-ai-core`（starter 已传递依赖）
    3. `AgentServiceApplication.java`：exclude 改 `org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration`（GA 把 auto-config 按 model/vector/mcp 拆包）
    4. `SpringAiConfig.java`：`new OpenAiApi(...)` → `OpenAiApi.builder().baseUrl(...).apiKey(...).restClientBuilder(...).webClientBuilder(...).build()`；`new OpenAiChatModel(...)` → `OpenAiChatModel.builder().openAiApi(...).defaultOptions(...).build()`；两个拦截器（`LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor`）通过 `restClientBuilder` 仍挂在 RestClient 链上
    5. `RiskAnalyzeService.java` 无需改：`chatClient.prompt().system().user().call().content()` fluent API 在 GA 保留
  - Smoke 项 1：`mvn -pl agent-service -am clean compile` 40 文件全过（M6→GA 后第一次 clean 编译，依赖能解析）
  - Smoke 项 2-4 留用户实跑：`/agent/api/v1/task/trigger/{id}` 一次 → 看 `/actuator/prometheus` 的 `educare_llm_*` 仍增长 + Langfuse UI 出现 trace
- [x] **H-1.2** `student-data-server`（Java）—— tools: `get_student_profile / get_academic_history / get_mental_indicators / get_attendance`
  - 完成于 2026-05-19：起独立微服务 `backend/mcp-student-data`（端口 8094），不内嵌 agent-service。模块入口 `McpStudentDataApplication.java` 通过 `MethodToolCallbackProvider` 显式登记 `StudentDataTools` 的 4 个 `@Tool`（1.0.x starter 不自动扫描，必须手动登记）。
  - **DDL 增量**：`sql/init/04_student_extras.sql` 新建 `student_academic_record` + `student_attendance`，含种子数据 student_id=1 共 3 门课 + 4 天考勤。
  - **student-service 域扩展**：`AcademicRecord`/`Attendance` entity + mapper + service + 两个 controller，端点 `GET /student/{id}/academic` / `/student/{id}/attendance` / `/student/{id}/attendance/summary`。不动既有 `StudentController.java`。
  - **mcp-student-data 模块**：pom 引 `spring-ai-starter-mcp-server-webmvc` + Nacos + Feign。`StudentDataTools` 通过 Feign 调 student-service + mental-service；`get_mental_indicators` 先 Feign 取 `Student.userId` 再调 `/mental/student/assessments?userId=`，保持 mental-service 改动 0。
  - **MCP_DESIGN.md 校准**：starter artifactId 改 `spring-ai-starter-mcp-server-webmvc`（GA 重命名）；1.0.x 用 `@Tool`+`@ToolParam` 且需 `MethodToolCallbackProvider` 手动登记；SSE 端点 `/sse`（连接）+ `/mcp/message`（消息）。
  - Smoke 待用户实跑：加载 04_student_extras.sql → `mvn -pl mcp-student-data spring-boot:run` → mcp-inspector 选 **Streamable HTTP** `http://localhost:8094/mcp`（H-1.1.6 后） → 4 个 tool 各调一次。
- [x] **H-1.1.6** Spring AI `1.0.0 → 1.1.6` GA 升级 + MCP transport 全栈切 Streamable HTTP（H-1.3 前置）
  - 完成于 2026-05-20：4 处改动 ——
    1. `backend/pom.xml`：`spring-ai.version` 改 `1.1.6`；MCP Java SDK 随之升到 0.18.2（依赖树验证）。1.0→1.1 核心 API（`@Tool`/`@ToolParam`/`MethodToolCallbackProvider`/`OpenAiApi.builder()`/`OpenAiChatModel.builder()`/`OpenAiChatAutoConfiguration`）保持 source-compatible，agent-service 40 文件 + mcp-student-data 8 文件 `mvn clean compile` 全过，零代码改动
    2. `mcp-student-data/application.yml`：删除 `stdio` / `sse-endpoint` / `sse-message-endpoint`；新增 `spring.ai.mcp.server.protocol=STREAMABLE` + `spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp`（实际 property key 通过 spring-ai-autoconfigure-mcp-server-common 1.1.6 的 spring-configuration-metadata.json 核实）
    3. `mcp-student-data` Javadoc 注释：`McpStudentDataApplication.java` + `StudentDataTools.java` 顶注 Spring AI 版本与传输协议表述更新
    4. `MCP_DESIGN.md §1/§3/§4/§9` + `deploy.md` 端口表协议列同步：拍板表 transport `SSE/streamable HTTP` → `Streamable HTTP`；§3 整段重写为"为什么选 Streamable HTTP"；§4 端点表 `/sse + /mcp/message` → 单端点 `/mcp`
  - Smoke 项 1：`mvn -pl mcp-student-data,agent-service -am clean compile` 全过
  - Smoke 项 2-4 留用户实跑：`mvn -pl mcp-student-data spring-boot:run` 后日志确认 `protocol=STREAMABLE` + mcp-inspector 选 Streamable HTTP → `http://localhost:8094/mcp` → 4 tool 可见
- [x] **H-1.3** `knowledge-rag-server`（Python FastMCP **2.x**，**Streamable HTTP**，端口 8095）—— tools: `search_cases / search_policies / search_psychology`
  - 完成于 2026-05-20：4 处改动 ——
    1. `ai-inference-service/app/services/rag_pipeline.py` 新文件：单 query × 单集合检索（embed → milvus_search → 可选 rerank → Redis 缓存），与 `app/api/rag.py` 的多源聚合管线职责分离；缓存 key `edu:rag:pipe:<sha256>`，TTL 沿用 `settings.RAG_CACHE_TTL`
    2. `ai-inference-service/app/mcp/` 新模块：`__init__.py` + `rag_adapter.py`（`search_collection` 入口，`top_k` 裁剪到 [1, 20]，空 query 早退） + `tools.py`（3 个 FastMCP `@mcp.tool` async：`search_cases`/`search_policies`/`search_psychology`，分别打到 `case`/`policy`/`psychology` collection） + `knowledge_rag_server.py`（独立进程 main，`mcp.run(transport="http", host, port, path)`）
    3. `requirements.txt` 加 `fastmcp>=2.3,<3.0`（原生 Streamable HTTP，spec 2025-03-26）
    4. `app/core/config.py` 加 `MCP_KNOWLEDGE_RAG_PORT=8095` + `MCP_KNOWLEDGE_RAG_PATH=/mcp`
  - **复用而非重写**：embedding_client / milvus_client / reranker_client / redis_client 全部沿用；`rag.py` `/api/v1/rag/retrieve` 路由零改动（其多源聚合语义与 MCP 单源 tool 不重合，不强抽公共层）
  - **独立进程**：不合入 FastAPI 主进程（8090），8095 独立 Uvicorn，资源/重启/调试独立；docker-compose 编排留 H-1.4 smoke 时再加
  - Smoke 项 1：`python3 -c "ast.parse(...)"` 5 文件语法全过
  - Smoke 项 2-4 留用户实跑：`pip install -r requirements.txt` → `python -m app.mcp.knowledge_rag_server` → mcp-inspector Streamable HTTP `http://localhost:8095/mcp` 验 3 tool 各调一次
- [x] **H-1.4** 写一个 `mcp-smoke-test` 脚本，从 Claude Desktop / agent-service 直连两个 server 跑 happy path
  - 完成于 2026-05-21：`scripts/mcp_smoke_test.sh`（纯 `curl + jq`，无 npm 依赖）一键回归两端 MCP server 的 Streamable HTTP 握手 → `notifications/initialized` → `tools/list` 校验 → 7 个 tool（`get_student_profile / get_academic_history / get_mental_indicators / get_attendance` + `search_cases / search_policies / search_psychology`）各调一次 happy path
  - 实现要点：`declare -A SESSION_ID` 同时管理两端 `Mcp-Session-Id` 自动接力；脚本起手 `BASH_VERSINFO` 检测（mac 默认 3.2 给提示 + exit 1）；`Accept: application/json, text/event-stream` 双声明；single failures 记录到 `FAIL` 不中断，末尾按 exit code 汇总
  - 降级语义：`tools/call` 返回 `content` 空 → ⚠（不 fail，对应下游 student-service / Milvus 没起的情形）；返回 `error` 字段 → ✗（exit 1）
  - 用法：`bash scripts/mcp_smoke_test.sh`，或自定义 `STUDENT_ID=<n> STUDENT_DATA_URL=... KNOWLEDGE_RAG_URL=...`
  - 取代手动 `npx @modelcontextprotocol/inspector` 点击 7 个 tool 的 H-1.2/H-1.3 smoke 流程，给后续 Spring AI / FastMCP / Milvus 升级提供回归 baseline

### H-2 主 Agent Loop（替换 `AgentTaskServiceImpl.doExecute`）

- [x] **H-2.1** 新建 `agent-service/.../core/AgentLoop.java`，实现 think→tool→observe 循环（参考 Claude Agent SDK）
  - 完成于 2026-05-21：新建包 `com.edu.agent.core`，5 个 Java 源（`AgentLoop` `@Component` 主循环 + `AgentLoopRequest`/`AgentLoopResult`/`AgentTrace` record + `AgentLoopStatus` enum）；选 **ReAct JSON 协议**（每轮 LLM 输出 `{"thought":..., "action":{...}}` 或 `{"thought":..., "final_answer":...}`），不走 Spring AI 1.1.6 `ChatClient.tools(...)` 的隐式内部循环 —— 让 thought / action / observation 三类事件可见、可单测、可后续接 Langfuse trace
  - 默认 `max_iterations=5`、`CONSECUTIVE_PARSE_ERROR_LIMIT=2`、`TOOL_RESULT_MAX_LEN=4096`、tool 调用失败一次重试，再失败立即 `TOOL_ERROR` 终止；4 种终止态枚举 `COMPLETED / MAX_ITERATIONS / TOOL_ERROR / PARSE_ERROR`
  - 工具列表类型用 `List<ToolCallback>`（`org.springframework.ai.tool.ToolCallback`），H-2.2 接 MCP 时 `ToolCallbackProvider` 直接喂入，AgentLoop 零改动
  - `AgentLoopTest.java` 3 case 全绿（`mvn -pl agent-service test -Dtest=AgentLoopTest`）：纯 Mockito `RETURNS_DEEP_STUBS` + JUnit 5，无 `@SpringBootTest`；覆盖 first-turn final / tool→final / max_iterations 触发；agent-service `pom.xml` 增加 `spring-boot-starter-test` scope=test
  - 不动 `AgentTaskServiceImpl.doExecute` 旧 4 阶段（feature flag 留 H-2.3）；不引 `spring-ai-starter-mcp-client-webmvc` 依赖（H-2.2 时一并加）；不接 Langfuse（H-2.2 一并接）
- [ ] **H-2.2** 接入 G-1 的 prompt caching + G-5 的 Langfuse
- [ ] **H-2.3** Feature flag：`educare.agent.loop.enabled`，默认 false；旧 4 阶段保留作 fallback
- [ ] **H-2.4** 灰度切流脚本（10% → 50% → 100%），观察 eval 集回归

### H-3 Skill markdown 文件（4 个）

- [ ] **H-3.1** `backend/agent-service/skills/risk-assessment.md`
- [ ] **H-3.2** `backend/agent-service/skills/psychological-screening.md`
- [ ] **H-3.3** `backend/agent-service/skills/intervention-design.md`
- [ ] **H-3.4** `backend/agent-service/skills/compliance-audit.md`
- [ ] **H-3.5** Skill 加载器：按需读 markdown 注入 system prompt，热更新支持

### H-4 Model Router（本地 14B + 云端 API 双路由）

- [ ] **H-4.1** 宿主侧起本地 14B 实例（端口 8092），云端 API key 走 Nacos 加密配置
- [ ] **H-4.2** 新增 `agent-service/.../router/ModelRouter.java`：敏感/原始画像 → 本地 14B；方案/审核 → 云端
- [ ] **H-4.3** 加路由审计日志（每次决策记录 task_id + 选用模型 + 数据敏感级）

### H-5 Mem0 集成 + memory-server（视进度）

- [ ] **H-5.1** 评估 Mem0 vs Letta 当前稳定性，做选型决策
- [ ] **H-5.2** 实现记忆分层（Working / Episodic / Semantic / Procedural）
- [ ] **H-5.3** memory-server MCP（Python）暴露 `recall_student_history / save_episode / summarize_long_term`

### H-6 Braintrust eval gate 接入 CI（视进度）

- [ ] **H-6.1** GitHub Actions / 内部 CI workflow：PR 触发 eval 跑全集
- [ ] **H-6.2** 阈值：faithfulness ≥ baseline、等级一致率 ≥ 0.85，不达标阻塞合并

**Phase H 验收总标准**：
- 旧 `AgentTaskServiceImpl.doExecute()` 4 阶段 if-else 删除（fallback 保留通过 feature flag）
- 新增一个 Agent 仅需写 Skill + 注册 MCP tool，零 Java 改动
- eval 集回归通过率 ≥ baseline
- ModelRouter 决策可在 Langfuse 看到

---

## 4. Phase I —— 高级能力 + 业务闭环（第 7-9 周，最小版）

> 最小决策（IMPROVEMENT §0）：仅 I-1 Hybrid Retrieval + I-5 干预反馈闭环。I-2/I-3/I-4/I-6 列入储备。

### I-1 Hybrid Retrieval（向量 + BM25）

- [ ] **I-1.1** Docker compose 加 Elasticsearch 8.x（或评估 Milvus 2.4 内置 BM25 替代，决策点）
- [ ] **I-1.2** `ai-inference-service/app/services/hybrid_retrieval.py`：dense + BM25 并行召回 + RRF 融合
- [ ] **I-1.3** `knowledge-rag-server` 接入 hybrid，灰度对比纯 dense
- [ ] **I-1.4** RAGAS 离线评测：context_precision / answer_relevancy 必须 ≥ 纯 dense baseline

### I-5 干预反馈闭环

- [ ] **I-5.1** DB 表 `intervention_feedback`（task_id / counselor_id / score 1-5 / outcome / created_at）
- [ ] **I-5.2** 后端 `POST /agent/api/v1/intervention/feedback`
- [ ] **I-5.3** 前端：干预方案页加 "1 个月后跟进" 评分组件
- [ ] **I-5.4** 月度报表：把反馈数据回流给 H-5 的 procedural memory（如果 H-5 已上线）

### 储备项（不在本季度排期）

- [ ] **I-2** GraphRAG / Neo4j —— 等 I-1 RAG 评测结果再决定
- [ ] **I-3** 3 个 Subagent（班主任 / 心理咨询师 / 学业导师）
- [ ] **I-4** 合规框架（policy 文档 + audit_log + tool guard）
- [ ] **I-6** 学生时间线页面

**Phase I 最小验收**：
- Hybrid 在专有名词 query（如 "高数 II"、"SCL-90"）召回 top-3 命中率 ≥ 纯 dense + 15%
- 干预反馈闭环：教师可提交评分，后台可导出月度 CSV

---

## 5. 跨阶段依赖图

```
G-1 (caching) ──► G-1.5 metrics ──► G-3.3 Prometheus ──► H-2 接入 trace
G-3.1 (Micrometer 依赖)            ──► G-3.2 (Scheduler 指标)
G-5 (Langfuse) ──► H-2.2 (Loop 接 trace) ──► H-6 (eval gate)
G-6 (eval 50 例) ──► H-6 (CI gate) ──► I-1.4 (Hybrid 评测复用框架)
H-1 (MCP Server) ──► H-2 (Loop 用 MCP tool)
H-4 (ModelRouter) ──► H-2 (Loop 选模型)
```

阻塞性强依赖：**G-1 → G-3 → H-2** 是关键路径，先把 G-1 全部跑通再启动 H 阶段任何子项。

---

## 6. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-13 | 初版创建，根据现状盘点回填 G-1.1/G-1.2/G-1.3 完成态 | IMPROVEMENT v1.1 拍板后启动执行 |
| 2026-05-13 | G-1.5 标 `[~]` 半完成：Python 部分随 G-1.4 一同实现（`chat_completion_raw` 自动调 `record_llm_response`），Java 部分单列保留 | LangChain `ChatOpenAI.ainvoke` 隐藏原始响应；为拿 `timings` 必须 raw httpx，与 G-1.4 强耦合 |
| 2026-05-13 | G-1.6 标 `[~]` 半完成：Python diagnostics 端点 + 脚本 + 手册落地；Java 端点延后到 G-3.1 引入 Micrometer 时一起做 | 单独为 Java 起一个 controller 太碎，与 Phase G-3 Prometheus 路径合流更经济 |
| 2026-05-13 | G-2.2 拆为 a/b/c/d/e 五个子步 | 设计落实后发现 JWT 改造（roles claim）+ 注解定义 + advice 实现 + entity 标注 + 自动配置 各自独立可合并，单 PR 粒度更易 review |
| 2026-05-14 | G-5.1 选 Langfuse self-hosted v2 + `profiles: [langfuse]` 默认不启 | 已有 compose 编排可复用；v2 两容器够用，v3 的 clickhouse+worker 五容器对 resume 项目超量；profiles 让 default `up` 仍精简 |
| 2026-05-14 | G-6.1 选 promptfoo 而非 Braintrust SaaS | 无付费 tier；YAML+JSONL 随 git 版本化；Langfuse 已覆盖在线 trace，离线 eval 用轻量 CLI 互补 |
| 2026-05-19 | H-1.1 拆出 H-1.1.5：Spring AI 升级单独 PR | M6→GA 改动面（starter 改名 + auto-config 拆包 + OpenAiApi/Model builder 化）远超 MCP_DESIGN §2 预估的"4-arg constructor 仍可用"，单 PR 隔离回归风险 |
| 2026-05-19 | H-1.2 决定起独立微服务 `backend/mcp-student-data` 而非内嵌 agent-service；同步补 `student_academic_record` / `student_attendance` 两张表 DDL；并订正 MCP_DESIGN.md 中 starter artifactId / 注解 / SSE 端点三处事实错误 | 单一职责 + 端口独立（8094）便于 mcp-inspector 直连 smoke；agent-service 重启不影响 MCP server；与 H-1.3 Python 端口 8095 形成对称结构。MCP_DESIGN 原写法（`spring-ai-mcp-server-spring-boot-starter` + `@Tool` 自动扫描 + `/sse` 路径）在 1.0.0 GA 已重命名/拆分，落地实测后才发现并校准 |
| 2026-05-20 | 在 H-1.2 与 H-1.3 之间插入 H-1.1.6：Spring AI 1.0.0 → 1.1.6 GA + MCP 传输全栈切到 Streamable HTTP | MCP spec 2025-03-26 已将 SSE 标记 deprecated，Streamable HTTP 为推荐传输；Spring AI 1.0.x 仅支持 SSE/STDIO，必须升 1.1.x 才能切。提早切的好处：H-1.3 直接落 HTTP transport，未来 H-2 client 一次到位，避免后续二次返工。1.0→1.1 实测核心 API 完全兼容（`@Tool`/`@ToolParam`/`MethodToolCallbackProvider`/`OpenAiApi.builder()` 等保留），零代码改动；MCP server 仅 application.yml 三行配置切换 |
| 2026-05-20 | H-1.3 落地：抽 `rag_pipeline.py` 单库检索管线（与既有多源聚合 `rag.py` 职责分离）；新增 `app/mcp/` FastMCP 2.x 模块（3 个 `@mcp.tool` + adapter + server entry），跑独立进程端口 8095 / Streamable HTTP / 单端点 `/mcp` | 与 H-1.2 student-data MCP server（8094）形成对称结构；不合入 FastAPI 主进程（8090）以保资源/重启/调试独立；rag.py 路由零改动，只新增依赖（fastmcp）与新模块，最小风险面 |
| 2026-05-21 | H-1.4 落地 `scripts/mcp_smoke_test.sh`：纯 `curl + jq` 一键回归两个 MCP server 的 `initialize → notifications/initialized → tools/list → 7×tools/call`，session id 自动接力；H-1 子阶段全部完结，指针推进至 H-2.1 AgentLoop | 取代手动 `mcp-inspector` 点点点的 H-1.2/H-1.3 smoke 流程；脚本即基线，后续 Spring AI / FastMCP / Milvus 升级跑一次即可回归。无 npm 依赖（保留 macOS 默认 toolchain），但需要 bash≥4（`declare -A` 双 session id 接力），脚本头自检 + 提示 |
| 2026-05-21 | H-2.1 落地：新建 `com.edu.agent.core.AgentLoop` + 4 个 record/enum + 3-case 单元测试，think→tool→observe 循环走 ReAct JSON 协议 | 选 ReAct 而非 Spring AI 1.1.6 native tool calling 的理由：1.1.6 `ChatClient.tools(...)` 默认内部隐式循环，thought/action/observation 三类事件个体不可见，无法 trace、无法 inject early-stop、无法限 max_iterations；`internalToolExecutionEnabled=false` 路径未实测稳定。ReAct JSON 把控制流封装在 AgentLoop 内部，外部只需 `ToolCallback` 列表，H-2.2 接 MCP 时调用方零改动；后续模型升 32B+ 想换 native tool calling 也只改 AgentLoop 内部。本步不动旧 4 阶段，feature flag 留 H-2.3 |

---

## 7. 关联文档

- 设计源：`docs/educare/IMPROVEMENT_2026_MAY.md`
- 现状架构：`docs/educare/architecture.md`
- 部署：`docs/educare/deploy.md`
- 验收：`docs/educare/acceptance.md`
- 待创建：`docs/educare/COMPLIANCE.md`（Phase I-4 储备项）
- 已产出：`PROMPT_CACHE_VERIFY.md`（G-1.6）、`FIELD_PERMISSION.md`（G-2.1）、`FIELD_PERMISSION_VERIFY.md`（G-2.3）、`SCHEDULE_METRICS_VERIFY.md`（G-3.4）、`RAG_UPSERT_DESIGN.md`（G-4.1）、`eval/README.md`（G-6.4）、`MCP_DESIGN.md`（H-1.1）

---

## 8. 已知阻塞（Known Blockers）

> 本节登记 **跨任务、阻塞下游验证、但本次执行不修** 的预先存在问题。每条都有"何时解锁"标注。

- **B-1（mental-service 编译断）** — `QuestionnaireServiceImpl.java` 引用了 `Question` 实体上不存在的 `scoringRules / scaleMin / scaleMax / scaleLabels` 字段和 `QuestionService` 上不存在的 `deleteByQuestionnaireId / saveBatch(Long, List)` 方法（来自更早的 mental 子模块 WIP）。
  - 影响：`mvn -pl mental-service` 单独跑不过；G-2.3 端到端验证如果走 mental 接口会卡。
  - 何时解锁：G-2.3 前；或更早由 mental-service 拥有者补齐实体字段 + 服务方法。
  - 解决方向：(a) 给 `Question` 补 4 个字段 + 数据库迁移；(b) `QuestionService` 补 `deleteByQuestionnaireId` 和 `saveBatch`；(c) 或回滚 `QuestionnaireServiceImpl` 里这段逻辑。
  - 标注 G-2.2-d 完成时发现（2026-05-13）。MentalAssessment 实体本身已成功加注解；该 entity 编译独立 OK。
