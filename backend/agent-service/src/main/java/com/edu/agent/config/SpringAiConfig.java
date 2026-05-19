package com.edu.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.base-url:http://host.docker.internal:8091}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:dummy}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen2.5-14b}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:2048}")
    private Integer maxTokens;

    /**
     * G-1.4 / G-1.5 Java：自定义 RestClient.Builder，挂两个拦截器
     * <ul>
     *   <li>{@link LlamaCppCachePromptInterceptor} —— 请求 body 注入 cache_prompt=true</li>
     *   <li>{@link LlmMetricsInterceptor} —— 响应 body 读 llama.cpp timings，喂 Micrometer</li>
     * </ul>
     *
     * <p>用 BufferingClientHttpRequestFactory 让请求/响应 body 都可重读：
     *   - 请求侧 interceptor 改 body 后再放行
     *   - 响应侧 interceptor 抓 timings 但不消费 Spring AI 的后续读取
     *
     * <p>Spring AI 1.0.0 GA：OpenAiApi / OpenAiChatModel 改 builder 模式（M6 的直构造器已不可用）。
     */
    @Bean
    public OpenAiApi openAiApi(LlmMetricsInterceptor llmMetricsInterceptor) {
        RestClient.Builder restBuilder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor(new LlamaCppCachePromptInterceptor())
                .requestInterceptor(llmMetricsInterceptor);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restBuilder)
                .webClientBuilder(WebClient.builder())
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * ChatClient Fluent API（1.0.0 GA 保留）。
     * Builder 由 spring-ai-starter-model-openai 自动配置提供，基于上面的 OpenAiChatModel。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
