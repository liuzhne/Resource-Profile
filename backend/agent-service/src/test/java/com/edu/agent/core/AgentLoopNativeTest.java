package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J-3.4：native 协议双轨 —— protocol=native 走 Spring AI 原生 tool-calling，单条 trace 返回最终文本。
 */
class AgentLoopNativeTest {

    @Test
    void nativeProtocolUsesToolCallbacksAndReturnsContent() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).toolCallbacks(anyList()).call().content())
                .thenReturn("native 最终答复");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        ReflectionTestUtils.setField(loop, "protocol", "native");

        AgentLoopResult r = loop.run(new AgentLoopRequest("sys", "u", List.of(), 5, "native-test"));

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.finalAnswer()).isEqualTo("native 最终答复");
        assertThat(r.iterations()).isEqualTo(1);
        assertThat(r.traces()).hasSize(1);
    }

    @Test
    void defaultProtocolNullGoesReactPath() {
        // 单测 new AgentLoop(...) 时 protocol 字段为 null → 走 ReAct（非 native）
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"t\",\"final_answer\":\"done\"}");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        AgentLoopResult r = loop.run(new AgentLoopRequest("s", "u", List.of(), 5, "t"));
        assertThat(r.finalAnswer()).isEqualTo("done");
    }
}
