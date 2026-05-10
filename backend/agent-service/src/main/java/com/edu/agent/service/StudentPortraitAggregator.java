package com.edu.agent.service;

import com.alibaba.fastjson2.JSON;
import com.edu.agent.feign.DataServiceClient;
import com.edu.agent.feign.MentalServiceClient;
import com.edu.agent.feign.StudentServiceClient;
import com.edu.agent.security.AgentContextHolder;
import com.edu.agent.security.AgentSecurityContext;
import com.edu.agent.util.DataMasker;
import com.edu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentPortraitAggregator {

    private final StudentServiceClient studentServiceClient;
    private final MentalServiceClient mentalServiceClient;
    private final DataServiceClient dataServiceClient;
    private final DataMasker dataMasker;

    /**
     * 基于既有服务真实端点，聚合学生画像 → 脱敏 → 输出给 LLM
     *
     * 数据流：
     * 1. student-service: /student/{id} → 基础档案（确定可用）
     * 2. mental-service: /mental/analysis → 全校心理分析（尝试提取该学生）
     * 3. data-service: /data/dashboard/statistics → 全校统计（尝试提取该学生）
     */
    public String buildMaskedProfile(Long studentId) {
        // 设置角色上下文（后续从 JWT 解析，当前默认 counselor）
        AgentSecurityContext ctx = new AgentSecurityContext();
        ctx.setRole(AgentSecurityContext.ROLE_COUNSELOR);
        ctx.setSensitiveDataAllowed(false);
        AgentContextHolder.set(ctx);

        try {
            Map<String, Object> aggregate = new HashMap<>();

            // ========== ① 学生基础档案（student-service，FallbackFactory 接管异常）==========
            Result<Map<String, Object>> studentResult = studentServiceClient.getStudentById(studentId);
            if (isOk(studentResult)) {
                aggregate.putAll(studentResult.getData());
                log.info("从 student-service 获取基础档案成功: studentId={}", studentId);
            } else {
                aggregate.put("studentId", studentId);
                aggregate.put("dataSourceNote", "基础档案获取失败");
            }

            // ========== ② 心理健康维度（mental-service，全校接口降级提取）==========
            Result<Map<String, Object>> mentalResult = mentalServiceClient.getMentalAnalysis();
            if (isOk(mentalResult)) {
                Map<String, Object> mentalData = mentalResult.getData();
                if (mentalData.isEmpty()) {
                    aggregate.put("mentalMetrics", "数据缺失");
                } else if (mentalData.containsKey(studentId)) {
                    // 接口若返回 Map<studentId, metrics> 结构则取本人
                    Object studentMental = mentalData.get(studentId);
                    if (studentMental instanceof Map) {
                        aggregate.put("mentalMetrics", studentMental);
                        log.info("从 mental-service 提取到该学生心理指标");
                    }
                } else {
                    // 全校分析已获取但无个性化字段
                    aggregate.put("mentalContext", "全校心理分析已获取，该学生个性化指标需补充 /mental/record/{id} 接口");
                    log.warn("mental-service 返回中未包含 studentId={} 的个性化指标", studentId);
                }
            } else {
                aggregate.put("mentalMetrics", "数据缺失");
            }

            // ========== ③ 数据画像维度（data-service，全校接口降级提取）==========
            Result<Map<String, Object>> dataResult = dataServiceClient.getDashboardStatistics();
            if (isOk(dataResult)) {
                Map<String, Object> stats = dataResult.getData();
                if (stats.isEmpty()) {
                    aggregate.put("dataMetrics", "数据缺失");
                } else if (stats.containsKey("studentStats") && stats.get("studentStats") instanceof Map) {
                    Map<String, Object> studentStats = (Map<String, Object>) stats.get("studentStats");
                    if (studentStats.containsKey(studentId)) {
                        aggregate.put("dataMetrics", studentStats.get(studentId));
                        log.info("从 data-service 提取到该学生数据指标");
                    } else {
                        aggregate.put("dataMetrics", "该学生数据指标未在统计面板中");
                    }
                } else {
                    aggregate.put("dataContext", "全校统计已获取，该学生个性化指标需补充 /data/student/{id} 接口");
                    log.warn("data-service 返回中未包含 studentId={} 的个性化指标", studentId);
                }
            } else {
                aggregate.put("dataMetrics", "数据缺失");
            }

            // ========== ④ 构建原始 JSON → 脱敏 ==========
            String rawJson = JSON.toJSONString(aggregate);
            log.info("聚合原始画像完成: studentId={}, fields={}", studentId, aggregate.keySet());

            // 先分级脱敏，再轻度脱敏给 LLM
            String maskedJson = dataMasker.maskForLLM(dataMasker.maskProfile(rawJson));
            log.debug("脱敏后画像长度: {} bytes", maskedJson.length());

            return maskedJson;

        } finally {
            AgentContextHolder.clear();
        }
    }

    private static boolean isOk(Result<?> r) {
        return r != null && Integer.valueOf(200).equals(r.getCode()) && r.getData() != null;
    }
}