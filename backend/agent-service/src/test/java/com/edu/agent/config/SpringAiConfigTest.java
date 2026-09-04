package com.edu.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResponseErrorHandler;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class SpringAiConfigTest {

    @Test
    void customBeansKeepAutoConfiguredRetryComponents() {
        SpringAiConfig config = new SpringAiConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "http://localhost:8091");
        ReflectionTestUtils.setField(config, "apiKey", "test-key");
        ReflectionTestUtils.setField(config, "model", "test-model");
        ReflectionTestUtils.setField(config, "temperature", 0.3d);
        ReflectionTestUtils.setField(config, "maxTokens", 256);
        ReflectionTestUtils.setField(config, "cachePromptEnabled", false);

        ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);
        OpenAiApi api = config.openAiApi(mock(LlmMetricsInterceptor.class), errorHandler);
        assertSame(errorHandler, ReflectionTestUtils.getField(api, "responseErrorHandler"));

        RetryTemplate retryTemplate = new RetryTemplate();
        OpenAiChatModel chatModel = config.openAiChatModel(api, retryTemplate);
        assertSame(retryTemplate, ReflectionTestUtils.getField(chatModel, "retryTemplate"));
    }
}
