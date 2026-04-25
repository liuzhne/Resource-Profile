package com.edu.agent.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "ai-inference-service",
        url = "${ai.inference.url:http://host.docker.internal:8090}"
)
public interface AiInferenceClient {

    @PostMapping("/api/v1/agent/risk")
    String riskAnalyze(@RequestBody Map<String, Object> studentProfile);

    @PostMapping("/api/v1/rag/retrieve")
    String retrieveKnowledge(@RequestBody Map<String, Object> query);

    @PostMapping("/api/v1/agent/plan")
    String generatePlan(@RequestBody Map<String, Object> context);

    @PostMapping("/api/v1/agent/audit")
    String complianceAudit(@RequestBody Map<String, Object> planWithProfile);
}