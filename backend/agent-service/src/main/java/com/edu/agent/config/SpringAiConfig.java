package com.edu.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * 1.0.0-M6 核心：ChatClient Fluent API
     * 注入 ChatClient.Builder，由 spring-ai-openai-spring-boot-starter 自动配置提供
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}