# Resource-Profile 运行手册

> 最近更新：2026-09-01
> 适用范围：当前仓库的开发、测试、排错和发布准备。真实 AI/生产签字状态以 [`docs/educare/EXECUTION_PLAN.md`](./docs/educare/EXECUTION_PLAN.md) R-5/R-6 为准。

## 1. 前置条件

| 工具 | 要求 | 说明 |
|---|---|---|
| JDK | **17，且只能是 17.x** | Maven Enforcer 要求 `[17,18)` |
| Maven | 3.x | 后端多模块构建 |
| Node.js | 22（CI 基线） | 前端 Vite 8 构建 |
| npm | 与 Node 22 配套 | 必须优先使用 `npm ci` |
| Python | 3.10 | AI 服务与测试基线 |
| Docker Compose | Docker Compose v2 | MySQL/Redis/Nacos/Milvus/AI/生产覆盖层 |
| 其他 | `curl`、`jq`；MCP smoke 需 bash ≥4 | 健康检查与自动验收 |
| 可选模型 | OpenAI 兼容 :8091/:8092/:8093 | 生成、embedding、reranker |

先确认版本：

```bash
java -version
mvn -version
node --version
npm --version
python3 --version
docker compose version
```

## 2. 端口速查

| 端口 | 服务 | 端口 | 服务 |
|---:|---|---:|---|
| 5173 | 前端开发服务 | 8080 | gateway |
| 8081-8086 | auth/user/teacher/student/mental/data | 8087 | agent-service |
| 8090 | ai-inference-service | 8091 | 生成 LLM（宿主） |
| 8092 | embedding（宿主） | 8093 | reranker（宿主） |
| 8094 | student-data MCP | 8095 | knowledge-rag MCP |
| 3306 | MySQL | 6379 | Redis |
| 8848/9848 | Nacos | 19530/9091 | Milvus |
| 8000 | Attu | 3000 | Grafana |
| 3001 | Langfuse | 9090 | Prometheus |
| 80/443 | 生产 nginx | — | — |

注意：Langfuse 映射到宿主 3001；Grafana 映射到宿主 3000，二者可同时运行。

## 3. 首次准备

### 3.1 获取依赖并验证构建

```bash
cd backend
mvn -B -ntp clean install -DskipTests

cd ../frontend
npm ci

cd ../ai-inference-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3.2 开发数据

MySQL 容器首次创建数据卷时会按文件名顺序执行 `sql/init/`。后续修改初始化 SQL 不会自动重放；需要对现有库显式执行新增迁移，或在确认可丢弃本地数据后重建卷。

默认开发账号为 `admin/admin`、`teacher/teacher`、`student/student`。这些账号只用于本地；生产必须执行 `sql/prod/01_rotate_default_passwords.sql`。

## 4. 启动开发环境

### 4.1 重要现状

`docker/docker-compose.yml` **不是完整业务全栈**：它没有 auth/user/teacher/student/mental/data 六个 service 定义。推荐开发方式是 Docker 启基础设施和 Python RAG，Java 业务服务在宿主分别运行。

### 4.2 启动基础设施与 Python 服务

在仓库根目录执行：

```bash
cd docker
docker compose up -d mysql redis nacos etcd minio milvus-standalone attu ai-inference-service knowledge-rag-mcp
docker compose ps
```

若只开发普通 CRUD，可省略 Milvus、Attu、AI 和 knowledge-rag：

```bash
cd docker
docker compose up -d mysql redis nacos
```

### 4.3 启动本地模型

AgentLoop 最少需要生成 LLM：

```bash
curl -fsS http://localhost:8091/v1/models
```

完整 RAG 还需要：

```bash
bash scripts/start-embedding-server.sh
bash scripts/start-reranker-server.sh
curl -fsS http://localhost:8092/v1/models
```

仓库不包含生成 LLM 权重或统一启动脚本；请用本机 llama.cpp/vLLM 在 :8091 提供 OpenAI 兼容 API。

### 4.4 启动 Java 服务

先构建一次，以便各模块解析 `common`：

```bash
cd backend
mvn -B -ntp install -DskipTests
```

然后在独立终端按需运行。普通业务链至少启动 gateway、auth 及目标领域服务；Agent 全链还要启动 student、mental、data、mcp-student-data、agent-service。

每个 Java 终端先设置同一个开发 JWT 密钥；缺失或少于 32 字节时 `JwtUtil` 会让服务 fail-fast：

```bash
export JWT_SECRET=edu-portrait-dev-jwt-secret-change-in-prod-0123456789
```

```bash
cd backend
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl teacher-service spring-boot:run
mvn -pl student-service spring-boot:run
mvn -pl mental-service spring-boot:run
mvn -pl data-service spring-boot:run
mvn -pl mcp-student-data spring-boot:run
mvn -pl agent-service spring-boot:run
mvn -pl gateway spring-boot:run
```

上面每一行应在不同终端运行。agent-service 默认会在启动时连接 :8094/:8095 并拉取工具列表；两个 MCP 未就绪时它可能启动失败。

### 4.5 启动前端

```bash
cd frontend
npm run dev
```

访问 `http://localhost:5173`。Vite 把 `/api` 代理到 `http://localhost:8080` 并移除 `/api` 前缀。

### 4.6 基础健康检查

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8087/actuator/health
curl -fsS http://localhost:8090/health
curl -fsS http://localhost:8094/actuator/health
curl -fsS http://localhost:8091/v1/models
```

8095 当前用 TCP healthcheck；可用 `bash scripts/mcp_smoke_test.sh` 验证协议和工具。

## 5. 测试与质量门

### 5.1 后端

```bash
cd backend
mvn -B -ntp clean test
```

该命令同时生成各模块 `target/site/jacoco/`，并执行父 POM 中安全关键类行覆盖率 ≥80% 的定向门。

定向测试示例：

```bash
cd backend
mvn -pl agent-service test -Dtest=AgentLoopNativeTest
mvn -pl gateway test -Dtest=JwtAuthGlobalFilterTest
mvn -pl mcp-student-data test -Dtest=StudentDataToolsContractTest
```

### 5.2 Python

CI 使用最小、无外部服务测试依赖：

```bash
cd ai-inference-service
python3 -m venv .venv-test
source .venv-test/bin/activate
pip install -r tests/requirements.txt
python -m unittest discover -s tests -p "test_*.py" -v
```

### 5.3 前端

```bash
cd frontend
npm ci
npm run lint:check
npm run build
npm run size:check
npm audit --omit=dev --audit-level=high
```

`npm run lint` 带 `--fix` 会改文件；CI/检查场景使用只读的 `lint:check`。

### 5.4 Eval 与生产前置脚本

```bash
python3 eval/run_eval.py --validate-only --input eval/risk_assessment.jsonl
bash scripts/test-preflight-prod.sh
```

真模型风险 eval 只有在推理端点可达时才执行；目标等级一致率是 0.85，不能用 `--validate-only` 结果代替模型质量证据。

## 6. 功能验收

### 6.1 登录与网关安全门

要求 gateway、auth、student、Redis 可用：

```bash
bash scripts/gateway_verify.sh
```

通过判据：公开登录、无 token 401、有效 token 200、无效 token 401、`/_internal/` 403、登出后旧 token 401 共 6 项全部通过。

字段权限和 IDOR 另按 [`docs/educare/FIELD_PERMISSION_VERIFY.md`](./docs/educare/FIELD_PERMISSION_VERIFY.md) 用多角色账号验证。

### 6.2 MCP 工具契约

```bash
bash scripts/mcp_smoke_test.sh
```

通过判据：student-data 4 个工具和 knowledge-rag 3 个工具全部完成 initialize/list/call，脚本退出码为 0。当前脚本不会附 `X-MCP-Token`，只适用于 token 为空的隔离开发环境；生产打开 `EDUCARE_MCP_TOKEN` 后，应由带相同 token 的 agent-service 发起工具调用，并以 AgentLoop 真跑验证两端互验。

### 6.3 AgentLoop 真跑

先登录：

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.token')

TASK=$(curl -sS -X POST http://localhost:8080/agent/api/v1/task/trigger/1 \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data')

curl -sS http://localhost:8080/agent/api/v1/task/$TASK \
  -H "Authorization: Bearer $TOKEN" | jq '.data | {id,status,riskLevel,riskAnalysisResult,interventionPlan,complianceAudit}'
```

持续轮询直到终态。通过判据：

1. 状态是 `COMPLETED` 或合规拒绝的 `REJECTED`，不得是 `FAILED`。
2. `riskAnalysisResult` 与 `interventionPlan` 都是非空合法 JSON。
3. 日志显示真实 MCP 工具名被调用，且没有 `TOOL_DENIED`/重复 parse error。
4. 中高风险任务存在 `complianceAudit`；审核服务异常必须进入人工审核/REJECTED。
5. 配置 Langfuse 后，UI/API 中同时存在 `agent.loop` 和 `llm.chat` trace。

真实模型完整证据采集以 [`docs/educare/E2E_RUNBOOK.md`](./docs/educare/E2E_RUNBOOK.md) 和执行计划 R-5 为准。当前 `scripts/local_real_run.sh` 是历史桩 LLM 脚本，未覆盖真实 MCP/模型/鉴权全链，不能作为上线证据。

### 6.4 RAG 灌库与检索

当前主 FastAPI app 没有注册 `rag_upsert.router`，所以不要用 `/api/v1/rag/upsert` 做运行验收。先使用仓库脚本灌种子知识：

```bash
cd ai-inference-service
MILVUS_HOST=localhost python -m scripts.init_milvus
MILVUS_HOST=localhost EMBEDDING_BASE_URL=http://localhost:8092/v1 python -m scripts.seed_knowledge
```

`rag_upsert.py` 的实现和隔离测试可参考 [`docs/educare/RAG_UPSERT_DESIGN.md`](./docs/educare/RAG_UPSERT_DESIGN.md)，但只有主应用实际挂载 router、配置 `EDUCARE_ADMIN_TOKEN` 并通过 HTTP 验收后，才能把 `/api/v1/rag/upsert` 记为可用。RAG 质量通过判据是使用真 BGE 向量的质量集达到已固化 dense baseline；仅“返回非空”只能证明机械链路。

## 7. 常见排错

| 症状 | 检查 | 处理 |
|---|---|---|
| Maven 在 validate 直接失败 | `java -version`、`mvn -version` | 把 `JAVA_HOME` 切到 JDK 17；不要跳过 Enforcer |
| gateway 返回 503 | Redis 日志、`token:{userId}` | gateway 会话校验 fail-closed；恢复 Redis 与正确 `REDIS_PASSWORD` |
| 有 JWT 仍 401 | JWT secret、Redis 当前 token、是否重新登录 | gateway/auth/agent 必须使用同一 `JWT_SECRET`；重新登录取得当前 token |
| gateway 返回 503/404 service unavailable | Nacos readiness 和服务列表 | 确认目标服务已启动并注册；基础 compose 不含六个普通业务服务 |
| agent-service 启动失败 | :8094/:8095 health、MCP initialize 日志 | 先启动两个 MCP；核对 URL、endpoint `/mcp` 和 token |
| MCP 返回 401 | 三端 `EDUCARE_MCP_TOKEN` | 使用相同非空值；生产至少 32 字符 |
| Agent 任务 `FAILED` | agent 日志、`agent_task.status`、LLM 原始输出 | 先区分 LLM 连接、JSON parse、ToolGuard、工具调用和 DB CAS；不要直接改成 COMPLETED |
| RAG 返回空 chunks | :8092、Milvus collection、embedding dim、灌库记录 | 确认维度 1024、集合存在、真语料已 upsert；reranker 可临时关闭定位 |
| `/api/v1/rag/upsert` 返回 404 | FastAPI OpenAPI、`app/main.py` router 注册 | 当前主 app 未挂载 `rag_upsert.router`，不能当作运行能力 |
| `/api/v1/rag/upsert` 返回 503 | `EDUCARE_ADMIN_TOKEN` | 仅在 router 已挂载后适用；端点默认 fail-closed |
| 看不到 Langfuse trace | profile、project keys、`LANGFUSE_HOST` | 容器内用 `http://langfuse-server:3000`，宿主用 `http://localhost:3001`；key 为空时是预期 no-op |
| 前端请求 401 后跳登录 | 浏览器 token 与 gateway 会话 | 检查是否登出/重登导致旧 token 被覆盖；清理前端本地 token 后重登 |
| PDF 中文方块 | `EDUCARE_FONT_PATH` | 提供可读的 Noto Sans SC 字体并重启 agent-service |
| 端口占用 | `lsof -nP -iTCP:<port> -sTCP:LISTEN` | 停止冲突进程或显式调整服务端口和所有调用方 URL |

日志命令：

```bash
cd docker
docker compose logs --tail=200 -f gateway agent-service mcp-student-data knowledge-rag-mcp ai-inference-service
```

不要在排错时使用 `docker compose down -v`，它会删除 MySQL/Redis 数据卷。

## 8. 生产发布

### 8.1 发布前硬门

1. `docs/educare/EXECUTION_PLAN.md` R-5/R-6 的对应证据已经实际完成；未完成时不得宣称生产就绪。
2. Java、Python、前端、eval 与 preflight 测试全绿。
3. 真 Qwen AgentLoop、真 BGE dense baseline、Langfuse trace 已验收。
4. auth/user/teacher/student/mental/data 六个业务服务已有明确部署目标；当前基础 compose 不包含它们。
5. MySQL 备份与恢复、TLS、网关安全、字段权限、IDOR、MCP token、监控告警都已实跑。

### 8.2 密钥与证书

```bash
cp docker/.env.example docker/.env
# 编辑 docker/.env，替换全部 change-me；不要提交该文件
bash scripts/preflight-prod.sh
```

把 `fullchain.pem` 和 `privkey.pem` 放入 `docker/certs/`。新库初始化后必须轮换 admin/teacher/student 默认密码。

### 8.3 生成与检查部署配置

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

检查生成配置中所有数据/内部端口只绑定 `127.0.0.1`，确认 nginx 是唯一公网入口。只有在六个普通业务服务已经由外部部署提供或 compose 已补齐后，才执行完整发布：

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

可选观测栈：

```bash
cd docker
docker compose -f docker-compose.yml --profile langfuse up -d langfuse-postgres langfuse-server
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring up -d prometheus grafana
```

### 8.4 发布后验证

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

cd ..
GATEWAY=https://<domain>/api ADMIN_USER=admin ADMIN_PASS='<password>' bash scripts/gateway_verify.sh
```

随后真跑一条中高风险 Agent 任务，以此验证生产 MCP token 互验，并检查 MySQL 产物、Langfuse trace、Prometheus 指标和 Grafana 告警。运行 `scripts/backup-mysql.sh`，并在隔离环境用 `scripts/restore-mysql.sh` 做恢复演练。

## 9. 回滚

- 应用：保留上一版镜像 tag/构建产物和 compose 配置，恢复上一版本后 `docker compose up -d`；不要删除数据卷。
- 配置：恢复经版本管理/变更记录确认的 Nacos 与 `.env` 配置，并重启受影响服务。密钥回滚必须考虑已签发 JWT/MCP 会话失效。
- 数据库：初始化脚本以增量、向后兼容为原则。含破坏性 DDL 的发布必须先准备显式 down migration 或从已验证备份恢复；不得把 `down -v` 当回滚。
- AI：可临时将 `EDUCARE_AGENT_LOOP_ENABLED=false` 回落 legacy，但前提是 Python :8090、LLM 与 RAG 依赖可用；回落后仍须跑安全与结果验收。
- RAG：错误向量可按 doc_id 重灌；回滚 embedding 模型时必须同时匹配维度和重建对应集合/基线。

### RENDER-DEPLOY-20260901：Render Blueprint 启动与数据层排错

- 复现：在 Render Blueprint `exs-da9vgj942hec7392v2vg` 查看 MCP deploy
  `dep-daaie3ffdruc73ajt8pg`，可见 Tomcat 初始化后 Feign 报“URL not provided”并最终端口扫描超时；
  Agent deploy `dep-daaiic5g1s2s73d9t920` 在 `McpSyncClient.initialize` 失败退出。对网关发送一组明确
  不存在的合成账号，业务响应包含 `CannotGetJdbcConnectionException`，auth 日志底层为 MySQL
  `Connect timed out`。
- 修复后验证：

  ```bash
  cd backend
  mvn -B -ntp -pl mcp-student-data,agent-service -am test

  cd ../frontend
  npm run lint:check
  npm run build
  ```

  Blueprint 同步后依次检查：

  1. `edu-portrait-mcp-student` 日志包含 `Tomcat started`，`GET /actuator/health` 返回 200/UP。
  2. `edu-portrait-ai-inference` 的 `GET /api/v1/health` 返回 200，带正确 `X-MCP-Token` 的 `/mcp`
     initialize/tools/list 成功。
  3. `edu-portrait-agent` 在 120 秒窗口内完成两个 MCP initialize 并出现 `Started AgentServiceApplication`。
  4. 数据库必须是可接收 MySQL TCP 的 Private Service/外部托管实例；不得再使用普通 Web Service
     的 `.onrender.com:3306`。新库存在 `01`~`05` 全部脚本创建的表。
  5. 使用部署方授权的测试账号经 gateway 登录，验证 Redis 会话写入、`/user/info`、一条 Agent 任务
     与登出撤销。不得在未确认时向线上提交密码。
- 排错：先看依赖服务是否仍在冷启动，再核对 `STUDENT_SERVICE_URL`、`MENTAL_SERVICE_URL`、
  `DATA_SERVICE_URL`、`AI_INFERENCE_URL`、两个 MCP URL 与共享 token；若登录返回 JDBC 错误，优先
  修正 MySQL 服务类型/主机，不要增加 socket timeout 掩盖不可路由的地址。
- 回滚：应用代码可回退到 Render 上一 deploy；数据库服务类型和持久盘不在未备份时回退/删除。
  若新持久数据库尚未承载写入，可将服务环境变量切回已验证的外部数据库。MCP 120 秒窗口可恢复默认
  20 秒，但免费实例冷启动时 Agent 将再次 fail-fast。
- 验证状态：线上根因已验证；2026-09-01 本地 JDK 17 定向 Reactor 测试 125 例全绿，前端
  `lint:check` 0 error（1 条既有 Prettier warning）且生产构建通过，`render.yaml` YAML 解析与
  `git diff --check` 通过。修复后 Blueprint 同步、持久数据层与端到端登录仍待验证。

## 10. 修复方案的运行手册更新模板

每个修复方案在本文件追加或修改可执行步骤，并在维护记录使用与 `ARCHITECTURE.md`、`DECISIONS.md` 相同标识：

```markdown
### FIX-NNN：问题标题

- 复现：最小命令、输入和修复前观察。
- 修复后验证：实际执行的命令。
- 通过判据：状态码、日志、数据或测试断言。
- 排错：验证失败时检查顺序。
- 回滚：恢复方式与数据影响。
- 验证状态：已验证（日期/环境）或待验证（原因）。
```

## 11. 维护记录

| 日期/标识 | 变更 | 验证状态 |
|---|---|---|
| 2026-09-01 / DOC-BASELINE | 按当前脚本、CI、compose 和执行计划初始化启动/测试/排错/发布手册 | 文档链接与命令静态核对；未执行真实服务和生产发布 |
| 2026-09-01 / RENDER-DEPLOY-20260901 | 增加 Render MCP/Feign 冷启动与 MySQL 协议排错、验证和回滚步骤 | 本地后端 125 例、前端构建通过；修复后云端复验待完成 |
