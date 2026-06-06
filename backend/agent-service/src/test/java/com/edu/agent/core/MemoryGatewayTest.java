package com.edu.agent.core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J-2.2：MemoryGateway 单测（recall/save 解析 MCP 工具 + 降级）。
 */
class MemoryGatewayTest {

    private ToolCallback tool(String name, String ret) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = DefaultToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
        when(cb.getToolDefinition()).thenReturn(def);
        if (ret != null) {
            when(cb.call(anyString())).thenReturn(ret);
        }
        return cb;
    }

    @SuppressWarnings("unchecked")
    private MemoryGateway gateway(boolean enabled, ToolCallback... tools) {
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(tools);
        ObjectProvider<ToolCallbackProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(tools.length == 0 ? null : provider);
        MemoryGateway g = new MemoryGateway(op);
        ReflectionTestUtils.setField(g, "enabled", enabled);
        return g;
    }

    @Test
    void disabledRecallEmptyAndSaveNoop() {
        ToolCallback save = tool(MemoryGateway.TOOL_SAVE, "ok");
        MemoryGateway g = gateway(false, save);
        assertThat(g.recall(1)).isEmpty();
        g.save(1, "ep", null);
        verify(save, never()).call(anyString());
    }

    @Test
    void recallReturnsToolOutput() {
        ToolCallback recall = tool(MemoryGateway.TOOL_RECALL, "{\"episodes\":[\"挂科\"]}");
        MemoryGateway g = gateway(true, recall);
        assertThat(g.recall(7)).contains("episodes");
    }

    @Test
    void recallTruncatesLongOutput() {
        ToolCallback recall = tool(MemoryGateway.TOOL_RECALL, "x".repeat(MemoryGateway.RECALL_MAX_LEN + 500));
        MemoryGateway g = gateway(true, recall);
        assertThat(g.recall(7)).endsWith("...[truncated]");
    }

    @Test
    void recallToolAbsentDegradesToEmpty() {
        ToolCallback other = tool("some_other_tool", "x");
        MemoryGateway g = gateway(true, other);
        assertThat(g.recall(7)).isEmpty();
    }

    @Test
    void saveInvokesSaveToolWithStudentAndEpisode() {
        ToolCallback save = tool(MemoryGateway.TOOL_SAVE, "ok");
        MemoryGateway g = gateway(true, save);
        g.save(42, "研判：风险=LOW", "{\"task_id\":9}");
        ArgumentCaptor<String> args = ArgumentCaptor.forClass(String.class);
        verify(save).call(args.capture());
        assertThat(args.getValue()).contains("\"student_id\":\"42\"").contains("研判").contains("task_id");
    }

    @Test
    void jsonStrEscapes() {
        assertThat(MemoryGateway.jsonStr("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(MemoryGateway.jsonStr(null)).isEqualTo("\"\"");
    }
}
