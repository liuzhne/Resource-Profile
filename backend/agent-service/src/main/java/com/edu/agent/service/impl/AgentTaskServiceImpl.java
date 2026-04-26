package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.AgentTaskService;
import com.edu.agent.service.RiskAnalyzeService;
import com.edu.agent.service.StudentPortraitAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {

    private final AgentTaskMapper agentTaskMapper;
    private final AiInferenceClient aiInferenceClient;          // P3 阶段接入 RAG / 方案 / 合规
    private final StudentPortraitAggregator portraitAggregator;   // P2-5：基于真实端点的画像聚合
    private final RiskAnalyzeService riskAnalyzeService;          // P2-5：真实 LLM 风险识别
    private final StringRedisTemplate redisTemplate;

    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    private static final String LOCK_PREFIX = "agent:task:lock:";
    private static final long LOCK_EXPIRE_SECONDS = 120;

    // ==================== 1. 任务创建 ====================

    @Override
    @Transactional
    public Long createTask(Long studentId) {
        AgentTask task = new AgentTask();
        task.setStudentId(studentId);
        task.setStatus(TaskStatus.PENDING);
        agentTaskMapper.insert(task);
        log.info("创建 Agent 任务: taskId={}, studentId={}", task.getId(), studentId);
        return task.getId();
    }

    // ==================== 2. 异步执行入口 ====================

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
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey), lockValue);
        }
    }

    // ==================== 3. 4 阶段状态机 ====================

    private void doExecute(Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            log.warn("任务 {} 不存在或已删除", taskId);
            return;
        }

        log.info("任务 {} 开始执行，当前状态: {}", taskId, task.getStatus());

        // ========== 阶段 1: 风险识别（P2-5 真实接入）==========
        if (task.getStatus() == TaskStatus.PENDING) {
            transition(taskId, TaskStatus.PENDING, TaskStatus.RISK_ANALYZING);

            // 【关键修复】同步内存对象状态，防止 updateById 覆盖
            task.setStatus(TaskStatus.RISK_ANALYZING);

            // ① 聚合画像（调用既有真实端点）→ ② LLM 推理
            String riskJson = executeRiskAnalyze(task);
            task.setRiskAnalysisResult(riskJson);



            RiskLevel level = parseRiskLevel(riskJson);
            task.setRiskLevel(level);
//            agentTaskMapper.updateById(task);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getRiskAnalysisResult, task.getRiskAnalysisResult())
                    .set(AgentTask::getRiskLevel, task.getRiskLevel()));

            // 低风险/无风险 → 短路完成
            if (level == RiskLevel.NONE || level == RiskLevel.LOW) {
                transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.COMPLETED);
                completeTask(taskId);
                log.info("任务 {} 风险等级 {}，直接完成", taskId, level);
                return;
            }
        }

        // ========== 阶段 2: RAG 检索（P3-1 接入，当前 Mock）==========
        if (task.getStatus() == TaskStatus.RISK_ANALYZING) {
            transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.KNOWLEDGE_RETRIEVING);
            String knowledge = executeKnowledgeRetrieve(task);
            task.setRetrievedKnowledge(knowledge);
            agentTaskMapper.updateById(task);
        }

        // ========== 阶段 3: 方案生成（P3-2 接入，当前 Mock）==========
        if (task.getStatus() == TaskStatus.KNOWLEDGE_RETRIEVING) {
            transition(taskId, TaskStatus.KNOWLEDGE_RETRIEVING, TaskStatus.PLAN_GENERATING);
            String plan = executePlanGenerate(task);
            task.setInterventionPlan(plan);
            agentTaskMapper.updateById(task);
        }

        // ========== 阶段 4: 合规审核（P3-3 接入，当前 Mock）==========
        if (task.getStatus() == TaskStatus.PLAN_GENERATING) {
            transition(taskId, TaskStatus.PLAN_GENERATING, TaskStatus.COMPLIANCE_CHECKING);
            String audit = executeComplianceAudit(task);
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

    // ==================== 4. 各阶段执行方法 ====================

    /**
     * 阶段 1：真实风险识别
     * 调用既有服务真实端点（/student/{id} + /mental/analysis + /data/dashboard/statistics）
     */
    private String executeRiskAnalyze(AgentTask task) {
        Long studentId = task.getStudentId();
        log.info("任务 {} 开始风险识别，学生: {}", task.getId(), studentId);

        // ① 聚合画像（基于既有真实端点，mental/data 降级处理）
        String maskedProfile = portraitAggregator.buildMaskedProfile(studentId);

        // ② 调用本地 llama.cpp
        return riskAnalyzeService.analyzeRisk(maskedProfile);
    }

    /**
     * 阶段 2-4：Mock（P3 接入）
     */
    private String executeKnowledgeRetrieve(AgentTask task) {
        return "[{\"source_db\":\"case_db\",\"case_id\":\"C2024001\",\"relevance_score\":0.95}]";
    }

    private String executePlanGenerate(AgentTask task) {
        return "{\"report_title\":\"干预方案\",\"immediate_actions\":[]}";
    }

    private String executeComplianceAudit(AgentTask task) {
        return "{\"audit_passed\":true,\"audit_items\":[]}";
    }

    // ==================== 5. 状态机工具方法 ====================

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
        update.setCompletedAt(LocalDateTime.now());
        agentTaskMapper.updateById(update);
    }

    private void failTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(TaskStatus.FAILED);
        agentTaskMapper.updateById(update);
    }

    // ==================== 6. JSON 解析辅助 ====================

    private RiskLevel parseRiskLevel(String json) {
        try {
            JSONObject map = JSON.parseObject(json);
            String level = map.getString("risk_level");
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            log.error("解析风险等级失败: {}", json, e);
            return RiskLevel.MEDIUM;
        }
    }

    private boolean parseAuditPassed(String json) {
        try {
            JSONObject map = JSON.parseObject(json);
            return Boolean.TRUE.equals(map.getBoolean("audit_passed"));
        } catch (Exception e) {
            log.error("解析合规审核结果失败: {}", json, e);
            return false;
        }
    }
}