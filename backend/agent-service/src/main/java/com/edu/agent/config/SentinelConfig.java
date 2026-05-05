package com.edu.agent.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * 编码方式注册 Sentinel 流控规则。Dashboard 不可用时也能生效。
 * 资源名 "agent:trigger" 与 AgentTaskController#trigger 上的 @SentinelResource 对齐。
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @Value("${educare.rate-limit.trigger-qps:2}")
    private double triggerQps;

    @PostConstruct
    public void initFlowRules() {
        FlowRule rule = new FlowRule();
        rule.setResource("agent:trigger");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(triggerQps);
        rule.setLimitApp("default");
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(Collections.singletonList(rule));
        log.info("Sentinel 流控规则已加载: resource=agent:trigger, qps={}", triggerQps);
    }
}
