package com.edu.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalyzeService {

    private final ChatClient chatClient;

    /**
     * 使用 ChatClient Fluent API 调用 LLM
     * 1.0.0-M6 特性：.prompt().system().user().call().content()
     */
    public String analyzeRisk(String studentProfileJson) {
        String response = chatClient.prompt()
                .system("你是一位教育数据分析师，请严格按JSON格式输出学生风险分析结果，包含risk_level、risk_score、primary_risk_type字段")
                .user(studentProfileJson)
                .call()
                .content();

        log.info("LLM 风险识别结果: {}", response);
        return response;
    }

    /**
     * 结构化输出（M6 支持 BeanOutputConverter）
     * 后续可替换为：.call().entity(RiskAnalysisResult.class)
     */
    public <T> T analyzeRiskStructured(String studentProfileJson, Class<T> clazz) {
        return chatClient.prompt()
                .system("你是一位教育数据分析师，请严格按JSON格式输出学生风险分析结果")
                .user(studentProfileJson)
                .call()
                .entity(clazz);
    }
}