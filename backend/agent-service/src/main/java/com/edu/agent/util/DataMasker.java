package com.edu.agent.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.enums.SensitivityLevel;
import com.edu.agent.security.AgentContextHolder;
import com.edu.agent.security.AgentSecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class DataMasker {

    // ========== 极高敏感度：心理测评原始数据 ==========
    private static final Set<String> EXTREME_FIELDS = Set.of(
            "scl90RawScore", "anxietyScore", "depressionScore", "counselingTranscript"
    );

    // ========== 高敏感度：家庭经济与家庭结构 ==========
    private static final Set<String> HIGH_FIELDS = Set.of(
            "familyEconomicLevel", "isSingleParent", "isLeftBehind", "familyIncomeRange"
    );

    // ========== 中敏感度：学业明细 ==========
    private static final Set<String> MEDIUM_FIELDS = Set.of(
            "gpaDetail", "failedCourses", "absenceRecords", "dailyConsumption"
    );

    /**
     * 对聚合后的学生画像 JSON 进行分级脱敏
     * 输入：原始画像 JSON（来自 data-service / mental-service）
     * 输出：脱敏后的 JSON（可安全发送给 LLM 或展示给辅导员）
     */
    public String maskProfile(String originalJson) {
        if (originalJson == null || originalJson.isEmpty()) {
            return "{}";
        }

        JSONObject profile = JSON.parseObject(originalJson);
        JSONObject masked = new JSONObject();

        for (String key : profile.keySet()) {
            Object value = profile.get(key);
            SensitivityLevel level = classifyField(key);

            switch (level) {
                case EXTREME -> masked.put(key, maskExtreme(key, value));
                case HIGH -> masked.put(key, maskHigh(key, value));
                case MEDIUM -> masked.put(key, maskMedium(key, value));
                case LOW -> masked.put(key, value); // 低敏感原样保留
            }
        }

        log.debug("脱敏完成，原始字段数: {}, 脱敏字段数: {}", profile.size(), masked.size());
        return masked.toJSONString();
    }

    /**
     * 极高敏感度脱敏：心理测评原始分数
     * 规则：仅 psychologist / admin 可见原始值，其他角色替换为摘要标签
     */
    private Object maskExtreme(String key, Object value) {
        if (AgentContextHolder.hasRole(AgentSecurityContext.ROLE_PSYCHOLOGIST)
                || AgentContextHolder.hasRole(AgentSecurityContext.ROLE_ADMIN)) {
            return value; // 授权角色可见原始值
        }

        // 非授权角色：替换为风险摘要
        if (key.contains("Score")) {
            return "心理评估摘要：已评估（原始分数受限）";
        }
        if (key.contains("Transcript")) {
            return "[心理咨询记录：仅心理中心可查看]";
        }
        return "[受限数据]";
    }

    /**
     * 高敏感度脱敏：家庭经济与结构标签
     * 规则：辅导员可见"存在经济压力"，不暴露具体等级
     */
    private Object maskHigh(String key, Object value) {
        if (AgentContextHolder.hasRole(AgentSecurityContext.ROLE_ADMIN)) {
            return value;
        }

        if (key.contains("Economic") || key.contains("Income")) {
            return "存在经济压力"; // 文本标签，无具体数值
        }
        if (key.contains("SingleParent") || key.contains("LeftBehind")) {
            return "家庭结构需关注"; // 泛化描述
        }
        return "[敏感标签]";
    }

    /**
     * 中敏感度脱敏：学业明细
     * 规则：学业导师可见具体科目，辅导员可见趋势摘要
     */
    private Object maskMedium(String key, Object value) {
        if (AgentContextHolder.hasRole(AgentSecurityContext.ROLE_ACADEMIC_ADVISOR)
                || AgentContextHolder.hasRole(AgentSecurityContext.ROLE_ADMIN)) {
            return value;
        }

        if (key.contains("gpaDetail")) {
            return "GPA趋势：近期波动"; // 趋势描述，无具体科目分数
        }
        if (key.contains("failedCourses")) {
            return "挂科情况：存在学业困难科目"; // 不暴露具体科目名
        }
        if (key.contains("absence")) {
            return "考勤状态：需关注"; // 不暴露具体缺勤次数
        }
        return "[明细数据]";
    }

    private SensitivityLevel classifyField(String fieldName) {
        if (EXTREME_FIELDS.contains(fieldName)) return SensitivityLevel.EXTREME;
        if (HIGH_FIELDS.contains(fieldName)) return SensitivityLevel.HIGH;
        if (MEDIUM_FIELDS.contains(fieldName)) return SensitivityLevel.MEDIUM;
        return SensitivityLevel.LOW;
    }

    /**
     * 为 LLM 输入专门准备的"轻度脱敏"：保留趋势和异常信号，移除精确 PII
     * 用于：发送给 LLM 做风险识别（LLM 不需要知道具体科目名，但需要知道"挂了 2 门高学分课"）
     */
    public String maskForLLM(String originalJson) {
        JSONObject profile = JSON.parseObject(originalJson);

        // LLM 场景：保留统计特征，匿名化具体标识
        if (profile.containsKey("studentName")) {
            profile.put("studentName", "某学生");
        }
        if (profile.containsKey("studentId")) {
            profile.put("studentId", "S" + profile.getString("studentId").hashCode());
        }
        if (profile.containsKey("phone")) {
            profile.put("phone", "1**********");
        }

        // 心理分数：保留风险等级标签，不保留原始分
        if (profile.containsKey("scl90RawScore")) {
            int score = profile.getIntValue("scl90RawScore", 0);
            String level = score > 160 ? "高风险" : (score > 100 ? "中风险" : "正常");
            profile.put("mentalHealthLevel", level);
            profile.remove("scl90RawScore");
        }

        return profile.toJSONString();
    }
}