package com.edu.agent.router;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * H-4：ModelRouter 决策 + fail-safe 回落 + 审计计数单测（纯 Mockito，无 Spring 上下文）。
 */
class ModelRouterTest {

    private final ChatClient local = mock(ChatClient.class);
    private final ChatClient cloud = mock(ChatClient.class);

    private ModelRouter router(boolean cloudEnabled, String apiKey, MeterRegistry reg) {
        ModelRouter r = new ModelRouter(local, cloud);
        ReflectionTestUtils.setField(r, "cloudEnabled", cloudEnabled);
        ReflectionTestUtils.setField(r, "cloudApiKey", apiKey);
        if (reg != null) {
            ReflectionTestUtils.setField(r, "meterRegistry", reg);
        }
        return r;
    }

    @Test
    void sensitiveRawDataAlwaysLocal() {
        ModelRouter r = router(true, "sk-xxx", null);
        assertThat(r.decide("risk", true)).isEqualTo(ModelTier.LOCAL);
        assertThat(r.decide("plan", true)).isEqualTo(ModelTier.LOCAL);  // 敏感优先级高于阶段
    }

    @Test
    void planAndAuditStagesPreferCloud() {
        ModelRouter r = router(true, "sk-xxx", null);
        assertThat(r.decide("plan", false)).isEqualTo(ModelTier.CLOUD);
        assertThat(r.decide("audit", false)).isEqualTo(ModelTier.CLOUD);
        assertThat(r.decide("compliance", false)).isEqualTo(ModelTier.CLOUD);
    }

    @Test
    void unknownStageNonSensitiveLocal() {
        ModelRouter r = router(true, "sk-xxx", null);
        assertThat(r.decide("risk", false)).isEqualTo(ModelTier.LOCAL);
        assertThat(r.decide(null, false)).isEqualTo(ModelTier.LOCAL);
    }

    @Test
    void cloudReadyRequiresEnabledAndKey() {
        assertThat(router(false, "sk-xxx", null).cloudReady()).isFalse();
        assertThat(router(true, "", null).cloudReady()).isFalse();
        assertThat(router(true, "  ", null).cloudReady()).isFalse();
        assertThat(router(true, "sk-xxx", null).cloudReady()).isTrue();
    }

    @Test
    void routeFallsBackToLocalWhenCloudNotReady() {
        ModelRouter r = router(false, "", null);
        // decide=CLOUD 但未就绪 → 实际返回 local client
        assertThat(r.client(ModelTier.CLOUD)).isSameAs(local);
        assertThat(r.route(7L, "plan", false)).isSameAs(local);
    }

    @Test
    void routeUsesCloudWhenReady() {
        ModelRouter r = router(true, "sk-xxx", null);
        assertThat(r.route(7L, "plan", false)).isSameAs(cloud);
        assertThat(r.route(7L, "risk", true)).isSameAs(local);
    }

    @Test
    void auditCounterIncrementsWithActualTier() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        ModelRouter r = router(true, "sk-xxx", reg);
        r.route(1L, "plan", false);   // → cloud
        r.route(2L, "risk", true);    // → local
        r.route(3L, "plan", false);   // → cloud
        double cloudCount = reg.find("educare.model.routed").tag("tier", "cloud").counter().count();
        double localCount = reg.find("educare.model.routed").tag("tier", "local").counter().count();
        assertThat(cloudCount).isEqualTo(2.0);
        assertThat(localCount).isEqualTo(1.0);
    }
}
