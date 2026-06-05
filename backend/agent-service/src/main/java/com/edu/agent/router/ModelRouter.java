package com.edu.agent.router;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * H-4.2 / H-4.3：模型路由器。
 *
 * <p>按"数据敏感级 + 任务阶段"在本地/云端两个 tier 间选择 {@link ChatClient}，并对每次决策落审计日志：
 * <ul>
 *   <li><b>敏感/原始画像</b>（风险识别直接吃原始学生画像）→ {@link ModelTier#LOCAL}，数据不出本地</li>
 *   <li><b>方案/审核</b>（已脱敏、偏推理）→ {@link ModelTier#CLOUD}（云端未就绪时 fail-safe 回落本地）</li>
 * </ul>
 *
 * <p>云端"就绪"= {@code educare.model.cloud.enabled=true} 且 api-key 非空；否则一律本地。
 * 审计走独立 logger {@code MODEL_ROUTER_AUDIT}（可在 logback 单独配 appender）+ Micrometer
 * 计数器 {@code educare.model.routed{tier,stage}}。
 */
@Component
public class ModelRouter {

    private static final Logger AUDIT = LoggerFactory.getLogger("MODEL_ROUTER_AUDIT");

    private final ChatClient localChatClient;
    private final ChatClient cloudChatClient;

    @Value("${educare.model.cloud.enabled:false}")
    private boolean cloudEnabled;

    @Value("${educare.model.cloud.api-key:}")
    private String cloudApiKey;

    /** 可选；单测无参注入路径下为 null，审计计数做了 null 保护。 */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public ModelRouter(ChatClient localChatClient,
                       @Qualifier("cloudChatClient") ChatClient cloudChatClient) {
        this.localChatClient = localChatClient;
        this.cloudChatClient = cloudChatClient;
    }

    /** 云端是否真正可用（开关开 + key 非空）。 */
    public boolean cloudReady() {
        return cloudEnabled && cloudApiKey != null && !cloudApiKey.isBlank();
    }

    /**
     * 仅做决策不取 client：敏感原始数据强制本地；方案/审核类阶段倾向云端；其余本地。
     */
    public ModelTier decide(String stage, boolean sensitiveRawData) {
        if (sensitiveRawData) {
            return ModelTier.LOCAL;
        }
        String s = stage == null ? "" : stage.toLowerCase();
        switch (s) {
            case "plan":
            case "intervention":
            case "audit":
            case "compliance":
            case "review":
                return ModelTier.CLOUD;
            default:
                return ModelTier.LOCAL;
        }
    }

    /** 按 tier 直接取 client（CLOUD 但云端未就绪 → 回落本地），不落审计。 */
    public ChatClient client(ModelTier tier) {
        return (tier == ModelTier.CLOUD && cloudReady()) ? cloudChatClient : localChatClient;
    }

    /**
     * 决策 + fail-safe 回落 + 审计，返回应使用的 ChatClient。
     *
     * @param taskId          任务 id（未知场景传 -1）
     * @param stage           阶段标签（risk / plan / audit ...）
     * @param sensitiveRawData 本次输入是否含原始/敏感画像
     */
    public ChatClient route(long taskId, String stage, boolean sensitiveRawData) {
        ModelTier decided = decide(stage, sensitiveRawData);
        ModelTier actual = (decided == ModelTier.CLOUD && !cloudReady()) ? ModelTier.LOCAL : decided;
        audit(taskId, stage, sensitiveRawData, decided, actual);
        return actual == ModelTier.CLOUD ? cloudChatClient : localChatClient;
    }

    private void audit(long taskId, String stage, boolean sensitive, ModelTier decided, ModelTier actual) {
        boolean fellBack = decided != actual;
        AUDIT.info("model-route task_id={} stage={} sensitive={} decided={} actual={}{}",
                taskId, stage, sensitive, decided, actual,
                fellBack ? " (cloud-not-ready→fallback-local)" : "");
        if (meterRegistry != null) {
            meterRegistry.counter("educare.model.routed",
                    "tier", actual.name().toLowerCase(),
                    "stage", stage == null || stage.isBlank() ? "unknown" : stage)
                    .increment();
        }
    }
}
