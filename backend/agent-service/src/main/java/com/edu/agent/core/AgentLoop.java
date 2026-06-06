package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.config.LangfuseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H-2.1：think → tool → observe 循环骨架。
 *
 * <p>协议为 ReAct JSON：每轮 LLM 必须输出形如：
 * <pre>{"thought":"...","action":{"tool":"name","args":{...}}}</pre>
 * 或：
 * <pre>{"thought":"...","final_answer":"..."}</pre>
 *
 * <p>循环自行解析、自行调度 ToolCallback、自行决定 break。**不**使用 Spring AI 1.1.6
 * `ChatClient.prompt().tools(...)` 的隐式内部循环，目的是让 thought / action / observation
 * 三类事件个体可见、可单测、可（H-2.2 起）接 Langfuse trace。
 *
 * <p>本类不接 MCP client；`AgentLoopRequest.tools()` 是 ToolCallback 列表的入参，调用方负责注入。
 * H-2.2 接 MCP 时由 `ToolCallbackProvider` 提供。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private static final int TOOL_RESULT_MAX_LEN = 4096;
    private static final int CONSECUTIVE_PARSE_ERROR_LIMIT = 2;

    private final ChatClient chatClient;
    private final LangfuseClient langfuseClient;

    /** J-1.2：工具守卫。null（如单测 new AgentLoop(...)）→ 全放行，行为不变。 */
    @Autowired(required = false)
    private ToolGuard toolGuard;

    public AgentLoopResult run(AgentLoopRequest rawReq) {
        AgentLoopRequest req = rawReq.normalized();
        Instant runStart = Instant.now();
        String systemPrompt = composeSystemPrompt(req);
        List<AgentTrace> traces = new ArrayList<>(req.maxIterations());
        int consecutiveParseError = 0;
        String lastThought = null;

        for (int i = 1; i <= req.maxIterations(); i++) {
            long t0 = System.currentTimeMillis();
            String userPrompt = composeUserPromptWithHistory(req, traces);

            String raw;
            try {
                raw = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
            } catch (Exception e) {
                log.error("[AgentLoop][{}] LLM 调用异常 iter={}", req.taskTag(), i, e);
                traces.add(new AgentTrace(i, "", null, null, null, null,
                        System.currentTimeMillis() - t0, "llm-call-failed: " + e.getMessage()));
                return finishRun(req, new AgentLoopResult(AgentLoopStatus.TOOL_ERROR, null, traces, i), runStart);
            }

            ParsedTurn parsed = parseLlmJson(raw);

            if (parsed.finalAnswer != null) {
                traces.add(new AgentTrace(i, raw, parsed.thought, null, null, null,
                        System.currentTimeMillis() - t0, null));
                log.info("[AgentLoop][{}] iter={} COMPLETED finalAnswer={}",
                        req.taskTag(), i, abbreviate(parsed.finalAnswer));
                return finishRun(req, new AgentLoopResult(AgentLoopStatus.COMPLETED, parsed.finalAnswer, traces, i), runStart);
            }

            if (parsed.parseError != null) {
                consecutiveParseError++;
                traces.add(new AgentTrace(i, raw, null, null, null, null,
                        System.currentTimeMillis() - t0, parsed.parseError));
                if (consecutiveParseError >= CONSECUTIVE_PARSE_ERROR_LIMIT) {
                    log.warn("[AgentLoop][{}] iter={} 连续 {} 轮 parse error，退出",
                            req.taskTag(), i, consecutiveParseError);
                    return finishRun(req, new AgentLoopResult(AgentLoopStatus.PARSE_ERROR, null, traces, i), runStart);
                }
                continue;
            }
            consecutiveParseError = 0;

            if (parsed.toolName != null) {
                // J-1.2：工具守卫 —— 被拒不崩，转结构化 TOOL_DENIED observation 喂回 LLM，循环继续。
                if (toolGuard != null) {
                    ToolGuard.GuardDecision decision = toolGuard.check(parsed.toolName, parsed.toolArgs);
                    if (!decision.allowed()) {
                        String denied = "TOOL_DENIED: " + decision.reason();
                        traces.add(new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                                denied, System.currentTimeMillis() - t0, "tool-denied"));
                        log.warn("[AgentLoop][{}] iter={} tool={} 被守卫拒绝: {}",
                                req.taskTag(), i, parsed.toolName, decision.reason());
                        lastThought = parsed.thought;
                        continue;
                    }
                }
                String observation;
                try {
                    observation = invokeTool(req.tools(), parsed.toolName, parsed.toolArgs);
                } catch (Exception first) {
                    log.warn("[AgentLoop][{}] iter={} tool={} 首次失败，重试一次",
                            req.taskTag(), i, parsed.toolName, first);
                    try {
                        observation = invokeTool(req.tools(), parsed.toolName, parsed.toolArgs);
                    } catch (Exception second) {
                        traces.add(new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                                "ERROR: " + second.getMessage(),
                                System.currentTimeMillis() - t0,
                                "tool-failed-after-retry"));
                        log.error("[AgentLoop][{}] iter={} tool={} 重试仍失败，终止",
                                req.taskTag(), i, parsed.toolName, second);
                        return finishRun(req, new AgentLoopResult(AgentLoopStatus.TOOL_ERROR, null, traces, i), runStart);
                    }
                }
                String truncated = truncate(observation);
                traces.add(new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                        truncated, System.currentTimeMillis() - t0, null));
                log.info("[AgentLoop][{}] iter={} tool={} → {} bytes",
                        req.taskTag(), i, parsed.toolName,
                        observation == null ? 0 : observation.length());
                lastThought = parsed.thought;
                continue;
            }

            // 既无 action 也无 final_answer：合法但循环不推进，记 thought 后下一轮
            lastThought = parsed.thought;
            traces.add(new AgentTrace(i, raw, parsed.thought, null, null, null,
                    System.currentTimeMillis() - t0, null));
            log.info("[AgentLoop][{}] iter={} thought-only，继续", req.taskTag(), i);
        }

        log.warn("[AgentLoop][{}] 达到 maxIterations={}，强制退出",
                req.taskTag(), req.maxIterations());
        return finishRun(req,
                new AgentLoopResult(AgentLoopStatus.MAX_ITERATIONS, lastThought, traces, req.maxIterations()),
                runStart);
    }

    /**
     * H-2.2：所有终止路径汇集到这里上报 Langfuse 顶层 trace。
     *
     * <p>每轮 LLM call 仍由 {@code LlmMetricsInterceptor} 各自上报独立 {@code llm.chat} generation trace，
     * 两类 trace 并列、不嵌套（嵌套留 H-2.4 eval 调优时扩展）。
     * LangfuseClient 未配 key 时整体 no-op，失败 fail-soft。
     */
    private AgentLoopResult finishRun(AgentLoopRequest req, AgentLoopResult result, Instant runStart) {
        Instant runEnd = Instant.now();
        try {
            String tracesSummary = JSON.toJSONString(result.traces());
            if (tracesSummary.length() > 2048) {
                tracesSummary = tracesSummary.substring(0, 2048) + "...[truncated]";
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("task_tag", req.taskTag());
            metadata.put("iterations", result.iterations());
            metadata.put("status", result.status().name());
            metadata.put("traces_summary", tracesSummary);
            langfuseClient.traceGeneration(
                    "agent.loop",
                    "unknown",
                    req.userPrompt(),
                    result.finalAnswer(),
                    0L,
                    0L,
                    metadata,
                    runStart,
                    runEnd);
        } catch (Exception e) {
            log.debug("[AgentLoop][{}] Langfuse trace 上报触发异常（已忽略）: {}",
                    req.taskTag(), e.getMessage());
        }
        return result;
    }

    // -------- 拼接 prompt --------

    private String composeSystemPrompt(AgentLoopRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            sb.append(req.systemPrompt()).append("\n\n");
        }
        sb.append("# 可用工具\n");
        if (req.tools().isEmpty()) {
            sb.append("（本次无可用工具，请直接给出最终答案）\n");
        } else {
            for (ToolCallback cb : req.tools()) {
                ToolDefinition def = cb.getToolDefinition();
                sb.append("- name: ").append(def.name()).append('\n');
                sb.append("  description: ").append(def.description()).append('\n');
                String schema = def.inputSchema();
                if (schema != null && !schema.isBlank()) {
                    sb.append("  inputSchema: ").append(schema).append('\n');
                }
            }
        }
        sb.append("\n# 输出协议（ReAct JSON，严格遵守）\n");
        sb.append("每一轮必须只输出一个 JSON 对象，**不要**写任何解释文字或 Markdown 代码围栏。\n");
        sb.append("有两种合法形态，二选一：\n");
        sb.append("1. 调用工具：{\"thought\":\"<本轮思考>\",\"action\":{\"tool\":\"<工具名>\",\"args\":{<参数对象>}}}\n");
        sb.append("2. 给出最终答案：{\"thought\":\"<本轮思考>\",\"final_answer\":\"<最终答复字符串>\"}\n");
        sb.append("收到 Observation 后必须基于其内容继续推理或直接给出 final_answer，不要重复同一个 action。\n");
        return sb.toString();
    }

    private String composeUserPromptWithHistory(AgentLoopRequest req, List<AgentTrace> traces) {
        // J-1.1：委托 HistoryCompactor 做窗口压缩，避免每轮重放全量历史导致 prompt 无界增长。
        return HistoryCompactor.compose(req.userPrompt(), traces);
    }

    // -------- 解析 + 工具调度 --------

    /** ReAct JSON 解析；trim Markdown fence + 提第一个 {...} 容忍 LLM 输出闲话。 */
    ParsedTurn parseLlmJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedTurn.error("empty-llm-output");
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
            s = s.trim();
        }
        int lb = s.indexOf('{');
        int rb = s.lastIndexOf('}');
        if (lb < 0 || rb < lb) {
            return ParsedTurn.error("no-json-object");
        }
        String jsonStr = s.substring(lb, rb + 1);
        JSONObject obj;
        try {
            obj = JSON.parseObject(jsonStr);
        } catch (Exception e) {
            return ParsedTurn.error("json-parse-failed: " + e.getClass().getSimpleName());
        }
        if (obj == null) {
            return ParsedTurn.error("json-null");
        }

        String thought = obj.getString("thought");
        String finalAnswer = obj.getString("final_answer");
        JSONObject action = obj.getJSONObject("action");

        if (finalAnswer != null) {
            return new ParsedTurn(thought, finalAnswer, null, null, null);
        }
        if (action != null) {
            String toolName = action.getString("tool");
            if (toolName == null || toolName.isBlank()) {
                return ParsedTurn.error("action-missing-tool");
            }
            Object argsObj = action.get("args");
            String argsJson = argsObj == null ? "{}" : JSON.toJSONString(argsObj);
            return new ParsedTurn(thought, null, toolName, argsJson, null);
        }
        return new ParsedTurn(thought, null, null, null, null);
    }

    String invokeTool(List<ToolCallback> tools, String name, String argsJson) {
        for (ToolCallback cb : tools) {
            if (name.equals(cb.getToolDefinition().name())) {
                return cb.call(argsJson == null ? "{}" : argsJson);
            }
        }
        throw new IllegalArgumentException("未知工具: " + name);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= TOOL_RESULT_MAX_LEN) {
            return s;
        }
        return s.substring(0, TOOL_RESULT_MAX_LEN) + "...[truncated " + (s.length() - TOOL_RESULT_MAX_LEN) + "]";
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** 解析结果：四组互斥字段，{finalAnswer / toolName / 仅 thought / parseError}。 */
    record ParsedTurn(String thought, String finalAnswer, String toolName, String toolArgs, String parseError) {
        static ParsedTurn error(String err) {
            return new ParsedTurn(null, null, null, null, err);
        }
    }
}
