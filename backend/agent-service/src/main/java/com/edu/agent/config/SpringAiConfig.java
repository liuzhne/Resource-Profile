package com.edu.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SpringAiConfig {

    // H-4：本地 tier base-url。默认复用 spring.ai.openai.base-url；如需独立本地 14B 实例（如 8092）
    // 设 educare.model.local.base-url 即可，不影响其它使用 spring.ai.openai.* 的配置。
    @Value("${educare.model.local.base-url:${spring.ai.openai.base-url:http://host.docker.internal:8091}}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:dummy}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen2.5-14b}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:2048}")
    private Integer maxTokens;

    // ===== H-4：云端模型 tier（本地仍为上面的 @Primary 默认）=====
    @Value("${educare.model.cloud.base-url:https://api.openai.com}")
    private String cloudBaseUrl;

    @Value("${educare.model.cloud.api-key:}")
    private String cloudApiKey;

    @Value("${educare.model.cloud.model:gpt-4o-mini}")
    private String cloudModel;

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
    @Primary
    public OpenAiApi openAiApi(LlmMetricsInterceptor llmMetricsInterceptor) {
        // 本地 tier：挂 cache_prompt（llama.cpp slot cache）+ metrics 两个拦截器
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
    @Primary
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
     * 本地 tier ChatClient（@Primary）。Fluent API（1.0.0 GA 保留）。
     * Builder 由 spring-ai-starter-model-openai 自动配置提供，基于上面 @Primary 的 OpenAiChatModel。
     * AgentLoop / RiskAnalyzeService 注入裸 {@code ChatClient} 时拿到本地 tier。
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    // ===== H-4：云端 tier ChatClient（非 @Primary，由 ModelRouter 按 @Qualifier 取用）=====

    /**
     * 云端 tier：只挂 metrics 拦截器（不注 cache_prompt —— 那是 llama.cpp slot-cache 私有字段，
     * 标准 OpenAI 端点不识别）。base-url/key/model 走 educare.model.cloud.*，key 建议 Nacos 加密。
     */
    @Bean("cloudChatClient")
    public ChatClient cloudChatClient(LlmMetricsInterceptor llmMetricsInterceptor) {
        RestClient.Builder restBuilder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor(llmMetricsInterceptor);
        OpenAiApi cloudApi = OpenAiApi.builder()
                .baseUrl(cloudBaseUrl)
                .apiKey(cloudApiKey == null || cloudApiKey.isBlank() ? "dummy" : cloudApiKey)
                .restClientBuilder(restBuilder)
                .webClientBuilder(WebClient.builder())
                .build();
        OpenAiChatModel cloudChatModel = OpenAiChatModel.builder()
                .openAiApi(cloudApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(cloudModel)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
        return ChatClient.create(cloudChatModel);
    }
}
