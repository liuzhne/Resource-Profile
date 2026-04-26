package com.edu.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalyzeService {

    private final ChatClient chatClient;

    @Value("${educare.prompt.risk-analyze}")
    private String riskSystemPrompt;

    /**
     * 调用本地 llama.cpp（Spring AI M6 ChatClient）进行风险识别
     */
    public String analyzeRisk(String maskedProfileJson) {
        log.info("开始风险识别，输入长度: {} bytes", maskedProfileJson.length());

        try {
            String response = chatClient.prompt()
                    .system(riskSystemPrompt)
                    .user(maskedProfileJson)
                    .call()
                    .content();

            log.info("LLM 风险识别完成，输出: {}", response);

            // 基础校验：确保返回合法 JSON
            if (!response.trim().startsWith("{")) {
                log.error("LLM 返回非 JSON 格式: {}", response);
                return fallbackRiskResult();
            }

            return response;

        } catch (Exception e) {
            log.error("LLM 调用异常，降级返回默认风险", e);
            return fallbackRiskResult();
        }
    }

    private String fallbackRiskResult() {
        return "{\"risk_level\":\"medium\",\"risk_score\":50,\"primary_risk_type\":\"数据异常需人工复核\",\"root_cause_analysis\":\"LLM服务暂时不可用，默认中风险处理\"}";
    }
}