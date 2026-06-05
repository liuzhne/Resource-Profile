package com.edu.agent.core;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-2.4：AgentLoopCanaryGate 灰度分桶单测。
 *
 * <p>纯逻辑（无 Spring 上下文），用 {@link ReflectionTestUtils} 直接灌 {@code @Value} 字段。
 * 覆盖：enabled 总闸、percent 边界（0/100）、确定性、对 percent 的单调性、整体分布大致均匀。
 */
class AgentLoopCanaryGateTest {

    private AgentLoopCanaryGate gate(boolean enabled, int percent) {
        AgentLoopCanaryGate g = new AgentLoopCanaryGate();
        ReflectionTestUtils.setField(g, "enabled", enabled);
        ReflectionTestUtils.setField(g, "canaryPercent", percent);
        return g;
    }

    @Test
    void disabledNeverRoutesToLoop() {
        AgentLoopCanaryGate g = gate(false, 100);
        for (long id = 1; id <= 200; id++) {
            assertThat(g.shouldUseAgentLoop(id)).as("enabled=false 任何任务都不该走 AgentLoop").isFalse();
        }
    }

    @Test
    void zeroPercentNeverRoutesAndHundredAlways() {
        AgentLoopCanaryGate zero = gate(true, 0);
        AgentLoopCanaryGate full = gate(true, 100);
        for (long id = 1; id <= 200; id++) {
            assertThat(zero.shouldUseAgentLoop(id)).isFalse();
            assertThat(full.shouldUseAgentLoop(id)).isTrue();
        }
    }

    @Test
    void percentIsClampedOutsideRange() {
        assertThat(gate(true, -5).getCanaryPercent()).isZero();
        assertThat(gate(true, 250).getCanaryPercent()).isEqualTo(100);
        // 负数全 legacy、超 100 全 AgentLoop
        assertThat(gate(true, -5).shouldUseAgentLoop(42)).isFalse();
        assertThat(gate(true, 250).shouldUseAgentLoop(42)).isTrue();
    }

    @Test
    void routingIsDeterministic() {
        AgentLoopCanaryGate a = gate(true, 37);
        AgentLoopCanaryGate b = gate(true, 37);
        for (long id = 1; id <= 500; id++) {
            assertThat(a.shouldUseAgentLoop(id)).isEqualTo(b.shouldUseAgentLoop(id));
        }
    }

    /** 灰度放量单调：percent 从小到大，已命中的任务集合只增不减（不会把任务退回 legacy）。 */
    @Test
    void rampUpIsMonotonic() {
        int[] stages = {10, 50, 100};
        for (long id = 1; id <= 1000; id++) {
            boolean prev = false;
            for (int p : stages) {
                boolean cur = gate(true, p).shouldUseAgentLoop(id);
                if (prev) {
                    assertThat(cur).as("taskId=%d 在 percent 提升后不应退回 legacy", id).isTrue();
                }
                prev = cur;
            }
        }
    }

    /** 分布大致均匀：percent=50 时命中比例落在 [0.4, 0.6]，确保哈希没有系统性偏置。 */
    @Test
    void distributionRoughlyMatchesPercent() {
        AgentLoopCanaryGate g = gate(true, 50);
        int n = 10_000;
        int hit = 0;
        for (long id = 1; id <= n; id++) {
            if (g.shouldUseAgentLoop(id)) {
                hit++;
            }
        }
        double ratio = (double) hit / n;
        assertThat(ratio).isBetween(0.40, 0.60);
    }

    @Test
    void bucketAlwaysInRange() {
        for (long id = -1000; id <= 1000; id++) {
            int b = AgentLoopCanaryGate.bucketOf(id);
            assertThat(b).isBetween(0, 99);
        }
    }
}
