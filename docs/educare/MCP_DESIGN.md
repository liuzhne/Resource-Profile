# MCP 选型决策（H-1.1）

> **目标**：定 Phase H 瘦身版（仅 student-data + knowledge-rag 两个 MCP server + 主 Agent Loop）的 SDK 版本与传输模式，作为 H-1.2 / H-1.3 / H-2 落地的地基。
>
> **依据**：IMPROVEMENT §0 拍板 "Spring AI MCP 主力 + Python FastMCP 补 RAG/Memory"。

---

## 1. 拍板结论

| 项 | 选定 | 理由 |
|----|------|------|
| Spring AI 版本 | **升 `1.0.0-M6 → 1.0.0`（GA）** | M6 没有 MCP starter；GA 提供 `spring-ai-mcp-server-spring-boot-starter` + `spring-ai-mcp-client-spring-boot-starter` |
| Java MCP server SDK | `spring-ai-mcp-server-spring-boot-starter`（GA） | 与现有 Spring 生态一致；@Tool 注解暴露方法 |
| Python MCP server SDK | **FastMCP**（`fastmcp>=0.4`） | Python 生态主流，装饰器 API；与 LangChain/Milvus 集成自然 |
| 传输模式 | **统一用 SSE / streamable HTTP** | docker-compose 友好；跨语言统一；agent-service ↔ Python rag-server 必走网络，不能 stdio |
| MCP client 集成 | `spring-ai-mcp-client-spring-boot-starter`（GA） | 在 agent-service 配 server URL 列表，自动注册为 ChatClient 可用 tool |
| Agent Loop 选型 | Spring AI **`ChatClient` + `ToolCallback`**（M6 已有的 fluent API 在 GA 保留） | 无需引第二个 Agent 框架；Tool 调用循环由 Spring AI 内部处理 |

---

## 2. Spring AI 升级影响面（M6 → GA）

**已知不变**：
- `ChatClient.Builder` / `ChatClient.prompt().system().user().call().content()` 在 GA 里保留，签名兼容
- `OpenAiChatModel` / `OpenAiChatOptions` 保留
- `OpenAiApi` 构造签名 GA 有少量变化但 4-arg `(baseUrl, apiKey, RestClient.Builder, WebClient.Builder)` 形式仍可用

**已知会变**：
- 包路径 `org.springframework.ai.chat.client` 等没动
- 一些 internal class 重命名（`StructuredOutputConverter` 等），但我们没用到
- 自动配置类名 `OpenAiAutoConfiguration` GA 起改 `OpenAiChatAutoConfiguration` —— 影响 `AgentServiceApplication.java:11` 的 exclude 行，**升级时同步修**

**回归点**（升级 PR 必须 smoke 通过）：
1. `mvn -pl agent-service compile`
2. `RiskAnalyzeService.analyzeRisk` 端到端：发一次 `/agent/api/v1/task/trigger/{id}` 看 LLM 实际调用是否 200
3. `LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor` 仍被挂上（看 `/actuator/prometheus` 的 `educare_llm_*` 指标增长）
4. Langfuse trace 仍被推（看 Langfuse UI）

---

## 3. 传输模式：为什么不混用

**stdio 优势**：
- 父子进程，零网络栈，最低延迟
- 单机调试简单

**SSE 优势**：
- 跨进程跨容器跨主机
- 跨语言（Java client + Python server）天然支持
- 健康检查 / 重连 / 鉴权全走 HTTP 现成机制

**为什么不混用**：
- Java student-data server 也用 SSE 部署在独立端口（8094 候选）→ 与 Python rag-server（8095 候选）行为一致
- agent-service 的 MCP client 配置只需列两条 URL，不区分对端语言/进程模型
- docker-compose 里两个 server 都是普通 service，运维统一

**唯一退路**：Phase H 末期若性能不达标（每 tool call > 100ms），可把 student-data 改 stdio，但当前不预设。

---

## 4. 端口分配 + 配置草案

| 服务 | 端口 | 路径 |
|------|------|------|
| agent-service（MCP client） | 8087（已有） | — |
| student-data MCP server | **8094**（新） | SSE on `/sse` |
| knowledge-rag MCP server | **8095**（新） | SSE on `/sse` |

agent-service `application.yml` 草案：
```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            student-data:
              url: ${MCP_STUDENT_DATA_URL:http://localhost:8094}
            knowledge-rag:
              url: ${MCP_KNOWLEDGE_RAG_URL:http://localhost:8095}
```

---

## 5. 工具命名约定

| MCP Server | Tool name | Java/Python 实现 |
|-----------|-----------|------------------|
| student-data | `get_student_profile(student_id)` | Java |
| student-data | `get_academic_history(student_id)` | Java |
| student-data | `get_mental_indicators(student_id)` | Java |
| student-data | `get_attendance(student_id)` | Java |
| knowledge-rag | `search_cases(query, top_k)` | Python |
| knowledge-rag | `search_policies(query, top_k)` | Python |
| knowledge-rag | `search_psychology(query, top_k)` | Python |

**约定**：
- snake_case 命名（与 OpenAI tool calling 规约一致；Spring AI MCP 自动转）
- 所有参数显式 typed（避免 LLM 传错）
- 返回值始终是 JSON-serializable 的 dict / list（LLM 端解析稳）

---

## 6. H-1 子步分配（落进 EXECUTION_PLAN）

- **H-1.1（本步）** — 选型决策（本文件）
- **H-1.2** — student-data MCP server（Java，新模块 `backend/mcp-student-data/` 或在 agent-service 内嵌）
- **H-1.3** — knowledge-rag MCP server（Python，`ai-inference-service/mcp/rag/` 子包）
- **H-1.4** — `mcp-smoke-test.sh`：从 `mcp-inspector` CLI 或 Claude Desktop 直连两 server 跑 happy path

---

## 7. 风险与折中

| 风险 | 处理 |
|------|------|
| Spring AI GA 与 M6 不兼容引发 ChatClient regression | 升级单独 PR，附 smoke checklist 见 §2；通过后再开 H-1.2 |
| Spring AI MCP starter 在 GA 才稳，可能仍有 1.0.x patch 漂移 | 锁定具体小版本而非 `1.0.+`；每次升级跑 eval（G-6） |
| Python FastMCP 与 langchain-openai 0.0.8 同 venv 依赖冲突 | requirements.txt 升前先 `pip-compile` 解依赖；冲突大时把 rag-server 拆独立 venv（独立容器即可） |
| MCP 协议本身仍在演进（spec 版本） | 锁定 spec version（在客户端配置声明），server SDK 升级时跑 H-1.4 smoke |
| 端口 8094/8095 与未来其他服务冲突 | 加进 `docs/educare/deploy.md` 端口表（H-1.2 落地时同步） |

---

## 8. 不在本文件范围（明确推后）

- **MCP 鉴权**：v1 spec 鉴权为可选，本期 server 仅监听 docker 内网，不开 auth；公网暴露时再加 OAuth/PAT
- **Tool sandboxing**：所有 tool 当前都是只读查询，不需要审批；写入型 tool（如 intervention-server 的 `assign_to_counselor`）留 H 后续阶段
- **Memory MCP server**：原 H-5 任务，瘦身后 "视进度"，本文件不涉及

---

## 9. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-14 | 初版 | H-1.1 选型决策 |
