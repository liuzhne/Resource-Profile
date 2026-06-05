package com.edu.agent.core;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * H-2.4：AgentLoop 灰度切流闸门。
 *
 * <p>把 "是否对某个任务启用 AgentLoop 路径" 的决策从 {@code AgentTaskServiceImpl} 抽出来，单独放进一个
 * {@link RefreshScope} 小 bean —— 这样 Nacos 推送 {@code educare.agent.loop.*} 配置后能热生效，
 * 而无需把重型的 {@code AgentTaskServiceImpl}（注入了一堆 Feign/Redis/Executor 协作者）也变成 refresh-scope。
 *
 * <p><b>百分比灰度</b>：布尔开关无法表达 "10% → 50% → 100%"。这里用 {@code canary-percent}（0-100）配合
 * 一个 <b>按 taskId 确定性分桶</b> 的哈希：每个任务落到固定 bucket ∈ [0,99]，当且仅当 {@code bucket < percent}
 * 时走 AgentLoop。该映射对 percent 单调 —— 把 percent 从 10 拉到 50，原先命中的 10% 任务仍命中，
 * 不会把已经走 AgentLoop 的任务退回 legacy，符合灰度放量的直觉。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code enabled=false}（默认）→ 任何任务都走 legacy 4 阶段，percent 被忽略。</li>
 *   <li>{@code enabled=true, percent=0} → 全 legacy（用于切流前对照 baseline）。</li>
 *   <li>{@code enabled=true, percent=100}（enabled 时默认）→ 全 AgentLoop。</li>
 *   <li>{@code enabled=true, 0<percent<100} → 约 percent% 的任务走 AgentLoop，其余 legacy。</li>
 * </ul>
 */
@Slf4j
@Component
@RefreshScope
public class AgentLoopCanaryGate {

    /** 灰度桶总数；taskId 哈希后对它取模。 */
    private static final int BUCKETS = 100;

    /** 64-bit 混淆常数（Fibonacci hashing），把递增的 taskId 打散到全 bucket 区间，避免低位聚集。 */
    private static final long MIX = 0x9E3779B97F4A7C15L;

    @Value("${educare.agent.loop.enabled:false}")
    private boolean enabled;

    @Value("${educare.agent.loop.canary-percent:100}")
    private int canaryPercent;

    /**
     * 可选：路由决策计数器来源。单测用无参 {@code new AgentLoopCanaryGate()} 构造时为 null，
     * 增量逻辑做了 null 保护。Spring 运行时由容器注入，切流脚本可从 /actuator/prometheus 读
     * {@code educare_agent_loop_routed_total{path="loop|legacy"}} 观察真实分流比例。
     */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 判断给定任务是否走 AgentLoop 路径。确定性：同一 taskId 在 percent 不变时结果稳定。
     */
    public boolean shouldUseAgentLoop(long taskId) {
        if (!enabled) {
            return record(false);
        }
        int percent = clampPercent(canaryPercent);
        if (percent <= 0) {
            return record(false);
        }
        if (percent >= BUCKETS) {
            return record(true);
        }
        int bucket = bucketOf(taskId);
        boolean useLoop = bucket < percent;
        log.debug("H-2.4 灰度路由：taskId={} bucket={} percent={} → {}",
                taskId, bucket, percent, useLoop ? "AgentLoop" : "legacy");
        return record(useLoop);
    }

    /** 计数路由决策（loop / legacy）后原样返回，便于在 shouldUseAgentLoop 各分支收口埋点。 */
    private boolean record(boolean useLoop) {
        if (meterRegistry != null) {
            meterRegistry.counter("educare.agent.loop.routed", "path", useLoop ? "loop" : "legacy")
                    .increment();
        }
        return useLoop;
    }

    /** taskId → 固定 bucket ∈ [0, BUCKETS)。包级可见，便于单测验证分布与单调性。 */
    static int bucketOf(long taskId) {
        long mixed = (taskId * MIX);
        // 取高位（混淆后高位随机性更好）再对桶数取模，floorMod 保证非负
        return (int) Math.floorMod(mixed >>> 32, (long) BUCKETS);
    }

    private static int clampPercent(int p) {
        if (p < 0) {
            return 0;
        }
        return Math.min(p, BUCKETS);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCanaryPercent() {
        return clampPercent(canaryPercent);
    }
}
