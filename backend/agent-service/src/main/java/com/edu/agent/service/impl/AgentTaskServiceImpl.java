package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {

    private final AgentTaskMapper agentTaskMapper;
    private final AiInferenceClient aiInferenceClient;
    private final StringRedisTemplate redisTemplate;

    @Qualifier("agentExecutor")
    private final Executor agentExecutor; // 仅用于自检，实际用 @Async

    private static final String LOCK_PREFIX = "agent:task:lock:";
    private static final long LOCK_EXPIRE_SECONDS = 120;

    @Override
    @Transactional
    public Long createTask(String studentId) {
        AgentTask task = new AgentTask();
        task.setStudentId(studentId);
        task.setStatus(TaskStatus.PENDING);
        agentTaskMapper.insert(task);
        log.info("创建 Agent 任务: taskId={}, studentId={}", task.getId(), studentId);
        return task.getId();
    }

    @Override
    @Async("agentExecutor")
    public void asyncExecute(Long taskId) {
        String lockKey = LOCK_PREFIX + taskId;
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(locked)) {
            log.warn("任务 {} 正在执行中，跳过重复调度", taskId);
            return;
        }

        try {
            doExecute(taskId);
        } catch (Exception e) {
            log.error("任务 {} 执行异常", taskId, e);
            failTask(taskId);
        } finally {
            // Lua 原子释放锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey), lockValue);
        }
    }

    /**
     * 状态机核心：4 阶段流水线
     */
    private void doExecute(Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            return;
        }

        log.info("任务 {} 开始执行，当前状态: {}", taskId, task.getStatus());

        // ========== 阶段 1: 风险识别 ==========
        if (task.getStatus() == TaskStatus.PENDING) {
            transition(taskId, TaskStatus.PENDING, TaskStatus.RISK_ANALYZING);

            // TODO: P2-5 完成后接入真实 Feign 调用
            // 当前先用模拟数据验证状态机流转
            String riskJson = executeRiskAnalyzeMock(task);
            task.setRiskAnalysisResult(riskJson);

            // 解析风险等级（后续可用 JSON Schema 强校验）
            RiskLevel level = parseRiskLevel(riskJson);
            task.setRiskLevel(level);
            agentTaskMapper.updateById(task);

            // 低风险/无风险 -> 直接完成，跳过后续 Agent
            if (level == RiskLevel.NONE || level == RiskLevel.LOW) {
                transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.COMPLETED);
                completeTask(taskId);
                log.info("任务 {} 风险等级 {}，直接完成", taskId, level);
                return;
            }
        }

        // ========== 阶段 2: RAG 知识检索 ==========
        if (task.getStatus() == TaskStatus.RISK_ANALYZING) {
            transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.KNOWLEDGE_RETRIEVING);

            String knowledge = executeKnowledgeRetrieveMock(task);
            task.setRetrievedKnowledge(knowledge);
            agentTaskMapper.updateById(task);
        }

        // ========== 阶段 3: 干预方案生成 ==========
        if (task.getStatus() == TaskStatus.KNOWLEDGE_RETRIEVING) {
            transition(taskId, TaskStatus.KNOWLEDGE_RETRIEVING, TaskStatus.PLAN_GENERATING);

            String plan = executePlanGenerateMock(task);
            task.setInterventionPlan(plan);
            agentTaskMapper.updateById(task);
        }

        // ========== 阶段 4: 合规审核 ==========
        if (task.getStatus() == TaskStatus.PLAN_GENERATING) {
            transition(taskId, TaskStatus.PLAN_GENERATING, TaskStatus.COMPLIANCE_CHECKING);

            String audit = executeComplianceAuditMock(task);
            task.setComplianceAudit(audit);
            agentTaskMapper.updateById(task);

            boolean passed = parseAuditPassed(audit);
            if (!passed) {
                transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.REJECTED);
                log.warn("任务 {} 合规审核未通过，转人工", taskId);
                return;
            }
        }

        // ========== 完成 ==========
        if (task.getStatus() == TaskStatus.COMPLIANCE_CHECKING) {
            transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.COMPLETED);
            completeTask(taskId);
            log.info("任务 {} 全部完成", taskId);
        }
    }

    /**
     * 乐观锁状态流转
     */
    private void transition(Long taskId, TaskStatus from, TaskStatus to) {
        int rows = agentTaskMapper.updateStatus(taskId, from.name(), to.name());
        if (rows == 0) {
            throw new IllegalStateException(
                    String.format("任务 %d 状态流转失败: %s -> %s", taskId, from, to));
        }
        log.info("任务 {} 状态流转: {} -> {}", taskId, from, to);
    }

    private void completeTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setCompletedAt(java.time.LocalDateTime.now());
        agentTaskMapper.updateById(update);
    }

    private void failTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(TaskStatus.FAILED);
        agentTaskMapper.updateById(update);
    }

    // ==================== Mock 方法（P2-5 / P3 接入真实 AI 调用） ====================

    private String executeRiskAnalyzeMock(AgentTask task) {
        // TODO: 调用 data-service/mental-service 获取画像，再调用 aiInferenceClient.riskAnalyze()
        return "{\"risk_level\":\"high\",\"risk_score\":85,\"primary_risk_type\":\"学业滑坡\"}";
    }

    private String executeKnowledgeRetrieveMock(AgentTask task) {
        // TODO: 调用 aiInferenceClient.retrieveKnowledge(...)
        return "[{\"source_db\":\"case_db\",\"case_id\":\"C2024001\",\"relevance_score\":0.95}]";
    }

    private String executePlanGenerateMock(AgentTask task) {
        // TODO: 调用 aiInferenceClient.generatePlan(...)
        return "{\"report_title\":\"干预方案\",\"immediate_actions\":[]}";
    }

    private String executeComplianceAuditMock(AgentTask task) {
        // TODO: 调用 aiInferenceClient.complianceAudit(...)
        return "{\"audit_passed\":true,\"audit_items\":[]}";
    }

    // ==================== JSON 解析辅助 ====================

    private RiskLevel parseRiskLevel(String json) {
        try {
            Map<String, Object> map = JSON.parseObject(json);
            String level = (String) map.get("risk_level");
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            log.error("解析风险等级失败: {}", json, e);
            return RiskLevel.MEDIUM; // 默认中风险，走完整流程
        }
    }

    private boolean parseAuditPassed(String json) {
        try {
            Map<String, Object> map = JSON.parseObject(json);
            return Boolean.TRUE.equals(map.get("audit_passed"));
        } catch (Exception e) {
            log.error("解析合规审核结果失败: {}", json, e);
            return false;
        }
    }
}