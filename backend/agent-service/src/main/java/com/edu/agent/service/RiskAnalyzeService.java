package com.edu.agent.service;

import jakarta.annotation.PostConstruct;
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

    @Value("${educare.debug.force-risk-level:}")
    private String forceRiskLevel;

    @PostConstruct
    public void logDebugMode() {
        if (forceRiskLevel != null && !forceRiskLevel.isBlank()) {
            log.warn("=========================================================");
            log.warn(" ⚠  DEBUG 模式启用：force-risk-level = {}", forceRiskLevel);
            log.warn(" ⚠  风险识别将跳过 LLM，直接注入 stub（仅用于联调）");
            log.warn("=========================================================");
        } else {
            log.info("RiskAnalyzeService 启动：force-risk-level 空，使用真实 LLM");
        }
    }

    /**
     * 调用本地 llama.cpp（Spring AI M6 ChatClient）进行风险识别。
     * 若 educare.debug.force-risk-level 非空（仅冒烟用），跳过 LLM 直接返回 stub。
     */
    public String analyzeRisk(String maskedProfileJson) {
        if (forceRiskLevel != null && !forceRiskLevel.isBlank()) {
            log.warn("⚠ DEBUG 模式启用：强制风险等级 = {}（生产请关闭 EDUCARE_DEBUG_FORCE_RISK_LEVEL）",
                    forceRiskLevel);
            return buildForcedRiskJson(forceRiskLevel.toLowerCase());
        }

        log.info("开始风险识别，输入长度: {} bytes", maskedProfileJson.length());

        try {
            String response = chatClient.prompt()
                    .system(riskSystemPrompt)
                    .user(maskedProfileJson)
                    .call()
                    .content();

            log.info("LLM 风险识别完成，输出: {}", response);

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

    private String buildForcedRiskJson(String level) {
        return String.format(
                "{\"risk_level\":\"%s\",\"risk_score\":78,\"primary_risk_type\":\"调试综合风险\","
                        + "\"root_cause_analysis\":\"DEBUG 模式注入：跳过 LLM 风险识别，强制完整 4 阶段流水线\","
                        + "\"key_indicators\":[\"调试: forceLevel=%s\",\"GPA: 1.9(模拟)\",\"出勤率: 60%%(模拟)\"],"
                        + "\"recommended_intervention_types\":[\"学业辅导\",\"心理疏导\",\"家校沟通\"],"
                        + "\"urgency_reason\":\"调试链路验证 — 模拟风险等级触发完整 Agent 流水线\"}",
                level, level);
    }

    private String fallbackRiskResult() {
        return "{\"risk_level\":\"medium\",\"risk_score\":50,\"primary_risk_type\":\"数据异常需人工复核\",\"root_cause_analysis\":\"LLM服务暂时不可用，默认中风险处理\"}";
    }
}