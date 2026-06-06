package com.edu.agent.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * J-2.2：记忆接线。把 H-5 memory-server（8096）的 recall/save 工具接进 AgentLoop 流程：
 * loop 前 {@code recall} 注入该生历史背景、终态后 {@code save} 记一笔情景。
 *
 * <p>从 MCP {@link ToolCallbackProvider} 按工具名解析（recall_student_history / save_episode），
 * 默认关闭（{@code educare.agent.memory.enabled=false}）；开关关、工具缺失或调用异常一律优雅降级
 * （recall 返回空串、save no-op），不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryGateway {

    static final String TOOL_RECALL = "recall_student_history";
    static final String TOOL_SAVE = "save_episode";
    static final int RECALL_MAX_LEN = 1500;

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @Value("${educare.agent.memory.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /** 召回该生历史记忆文本（注入主 prompt 用）。降级返回空串。 */
    public String recall(long studentId) {
        if (!enabled) {
            return "";
        }
        ToolCallback tool = findTool(TOOL_RECALL);
        if (tool == null) {
            return "";
        }
        try {
            String args = "{\"student_id\":\"" + studentId + "\",\"top_k\":5}";
            String out = tool.call(args);
            if (out == null || out.isBlank()) {
                return "";
            }
            return out.length() > RECALL_MAX_LEN ? out.substring(0, RECALL_MAX_LEN) + "...[truncated]" : out;
        } catch (Exception e) {
            log.debug("memory recall 失败 sid={}: {}", studentId, e.getMessage());
            return "";
        }
    }

    /** 保存一条情景记忆。降级 no-op。 */
    public void save(long studentId, String episode, String metadataJson) {
        if (!enabled) {
            return;
        }
        ToolCallback tool = findTool(TOOL_SAVE);
        if (tool == null) {
            return;
        }
        try {
            String meta = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
            String args = "{\"student_id\":\"" + studentId + "\",\"episode\":" + jsonStr(episode)
                    + ",\"metadata\":" + meta + "}";
            tool.call(args);
        } catch (Exception e) {
            log.debug("memory save 失败 sid={}: {}", studentId, e.getMessage());
        }
    }

    private ToolCallback findTool(String name) {
        ToolCallbackProvider provider = toolCallbackProviders.getIfAvailable();
        if (provider == null) {
            return null;
        }
        ToolCallback[] cbs = provider.getToolCallbacks();
        if (cbs == null) {
            return null;
        }
        for (ToolCallback cb : cbs) {
            if (name.equals(cb.getToolDefinition().name())) {
                return cb;
            }
        }
        return null;
    }

    /** 极简 JSON 字符串转义（双引号/反斜杠/换行）。 */
    static String jsonStr(String s) {
        if (s == null) {
            return "\"\"";
        }
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + esc + "\"";
    }
}
