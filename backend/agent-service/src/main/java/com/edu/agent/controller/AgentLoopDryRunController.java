package com.edu.agent.controller;

import com.edu.agent.core.AgentLoop;
import com.edu.agent.core.AgentLoopRequest;
import com.edu.agent.core.AgentLoopResult;
import com.edu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * H-2.2：AgentLoop 临时手测端点 —— 把 MCP `ToolCallbackProvider`（Spring AI 1.1.6 auto-config
 * 合并 student-data + knowledge-rag 共 7 个工具）一次性喂给 {@link AgentLoop#run}，返回完整 traces。
 *
 * <p><b>仅供运维内部 curl 冒烟</b>：不挂 Sentinel / Security / JWT，不接 rate-limit。
 * gateway 路由 `/agent/**` 已配，端点暴露在 8080 网关下，但路径前缀 `_internal` 明示边界。
 *
 * <p>H-2.3 真切流到 {@code AgentTaskServiceImpl.doExecute} 时，本控制器应删除或挂上 admin role。
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1/_internal/loop")
@RequiredArgsConstructor
public class AgentLoopDryRunController {

    private static final int DEFAULT_MAX_ITERATIONS = 5;

    private final AgentLoop agentLoop;
    // ObjectProvider：MCP client 关闭/未就绪时 ToolCallbackProvider 缺失也不阻断启动（与 AgentTaskServiceImpl 一致）
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @PostMapping("/dry-run")
    public Result<AgentLoopResult> dryRun(@RequestBody DryRunRequest req) {
        ToolCallbackProvider provider = toolCallbackProviders.getIfAvailable();
        ToolCallback[] callbacks = provider == null ? null : provider.getToolCallbacks();
        List<ToolCallback> tools = callbacks == null ? List.of() : Arrays.asList(callbacks);
        log.info("[AgentLoopDryRun] taskTag={} tools={} userPromptLen={}",
                req.taskTag(), tools.size(),
                req.userPrompt() == null ? 0 : req.userPrompt().length());

        AgentLoopRequest agentReq = new AgentLoopRequest(
                req.systemPrompt(),
                req.userPrompt(),
                tools,
                req.maxIterations() == null ? DEFAULT_MAX_ITERATIONS : req.maxIterations(),
                req.taskTag() == null ? "dry-run" : req.taskTag()
        );
        return Result.success(agentLoop.run(agentReq));
    }

    /** Dry-run 入参：{@code userPrompt} 必填，其余可空。 */
    public record DryRunRequest(
            String systemPrompt,
            String userPrompt,
            Integer maxIterations,
            String taskTag
    ) {
    }
}
