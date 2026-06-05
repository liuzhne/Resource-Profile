package com.edu.agent.sse;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * F-2：风险任务终态事件发布器。
 * 单实例场景下也走 Redis Pub/Sub —— 同一应用内 publisher 与 subscriber 通过 Redis 解耦，
 * 加多实例时无需改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarningPublisher {

    public static final String CHANNEL = "edu:agent:warning:new";

    private final StringRedisTemplate redisTemplate;

    /**
     * Publishes a risk task terminal-state event to the Redis Pub/Sub channel.
     *
     * The published message contains `taskId`, `status`, `riskLevel`, `studentId`, and a `ts` timestamp.
     *
     * @param taskId    identifier of the task
     * @param status    terminal status of the task
     * @param riskLevel risk level associated with the task
     * @param studentId identifier of the student related to the task
     */
    public void publishTerminal(Long taskId, String status, String riskLevel, Long studentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status);
        payload.put("riskLevel", riskLevel);
        payload.put("studentId", studentId);
        payload.put("ts", System.currentTimeMillis());
        try {
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(payload));
            log.debug("F-2：发布终态事件 taskId={} status={}", taskId, status);
        } catch (Exception e) {
            log.warn("F-2：发布到 {} 失败：{}", CHANNEL, e.getMessage());
        }
    }
}
