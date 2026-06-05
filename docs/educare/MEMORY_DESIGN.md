# H-5 记忆层设计（Mem0 选型 + 分层 + memory-server）

> 创建：2026-06-05 ｜ 对应 EXECUTION_PLAN H-5.1 / H-5.2 / H-5.3

## 1. H-5.1 选型决策：Mem0 vs Letta vs 自实现

| 方案 | 优点 | 顾虑 |
|------|------|------|
| **Mem0** | 现成记忆抽取/检索 API、社区活跃 | SDK 仍快速演进、版本破坏性变更多；自带向量库/LLM 抽象与本项目既有 Milvus/llama.cpp 栈重叠，强行接入反增耦合 |
| **Letta(MemGPT)** | 完整 agent + 分页记忆 | 重，要起独立服务 + 自带 agent 框架，与本项目已落地的 AgentLoop（H-2）职责冲突 |
| **自实现（选定）** | 复用既有 Redis/Milvus/llama.cpp，零新外部依赖，可控可测 | 抽取/蒸馏质量靠自写 prompt，不如 Mem0 开箱 |

**决策：自实现轻量记忆层**。理由与项目其它决策一脉相承（G-5/G-6/H-1.3 都选"复用既有栈、避开不稳定外部 SDK"）：
1. resume 项目无付费/运维预算养额外服务；
2. Mem0/Letta 的向量与 LLM 抽象与本项目 Milvus + llama.cpp 重复，接入是负资产；
3. 记忆的**接口**（recall/save/summarize）比**实现**重要——先用 Redis 把闭环跑通，未来要换 Mem0 只需替换 `memory_store` 实现层，MCP 工具签名不变。

## 2. H-5.2 记忆分层

参考认知记忆模型，分四层（实现见 `app/services/memory_store.py`）：

| 层 | 含义 | 存储 | key / TTL |
|----|------|------|-----------|
| **Working** | 单次会话临时上下文 | Redis string | `edu:mem:working:{sid}` ／ 1h |
| **Episodic** | 离散事件流（预警/谈心/方案/反馈） | Redis list（LPUSH+LTRIM 有界） | `edu:mem:episode:{sid}` ／ 90d，上限 `MEMORY_EPISODE_MAX=50` |
| **Semantic** | 从情景蒸馏的稳定事实/画像摘要 | Redis string(JSON) | `edu:mem:semantic:{sid}` ／ 180d |
| **Procedural** | 被验证有效的干预做法 | 同 Semantic（摘要里的 `strategies` 字段） | 同上 |

降级：Redis 不可用时所有读写返回空/False，不抛异常（与既有 `redis_client` 一致）。

**未来升级路径**：Semantic 层可从 Redis string 升级为 Milvus 向量召回（embed facts → 跨学生相似检索 / 大规模长期记忆），届时只改 `memory_store.get_semantic/set_semantic` + 新增一个 memory collection，MCP 工具与 adapter 不变。

## 3. H-5.3 memory-server（MCP）

- FastMCP 2.x，独立进程，端口 **8096**，Streamable HTTP 单端点 `/mcp`，与 knowledge-rag(8095) 对称
- 启动：`cd ai-inference-service && python -m app.mcp.memory_server`
- 三个 tool（实现 `app/mcp/memory_adapter.py`，装饰层 `app/mcp/memory_tools.py`）：

| tool | 入参 | 行为 |
|------|------|------|
| `recall_student_history` | `student_id, query="", top_k=10` | 返回 semantic 摘要 + 最近情景（query 子串过滤，top_k∈[1,50]） |
| `save_episode` | `student_id, episode, metadata?` | 追加一条情景（LPUSH+LTRIM），metadata 可带 task_id/risk_level/type |
| `summarize_long_term` | `student_id` | 把情景经 LLM 蒸馏为 facts/strategies/summary 写回 semantic；LLM 不可用降级为情景原文拼接（`degraded=true`） |

## 4. 与 AgentLoop 的衔接（后续接线）

memory-server 与 student-data(8094)/knowledge-rag(8095) 并列，是 AgentLoop 的第三类 MCP 工具源。接线时在 agent-service `application.yml` 的 `spring.ai.mcp.client.streamable-http.connections` 增加 `memory` 连接（`localhost:8096/mcp`），AgentLoop 即自动获得 3 个记忆工具——典型用法：分析前 `recall_student_history` 补背景，出方案后 `save_episode` 记一笔，周期性 `summarize_long_term` 蒸馏。本步只交付 server，不强接 AgentLoop（避免每次任务都依赖 memory-server 在线），接线留灰度。

## 5. 已知限制

- Working 层目前仅预留 key 约定，未在 tool 暴露（单次会话上下文由 AgentLoop 内部 traces 承担，无需跨进程持久化）
- 蒸馏质量取决于 llama.cpp + prompt，未做 eval；情景去重/冲突消解未实现（追加式）
- 无跨学生语义检索（Semantic 仍按 sid 直取，Milvus 升级后才有相似召回）
