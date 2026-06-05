package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.AgentTaskService;
import com.edu.agent.service.RiskAnalyzeService;
import com.edu.agent.service.StudentPortraitAggregator;
import com.edu.agent.sse.WarningPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final WarningPublisher warningPublisher;              // F-2：终态事件发布

    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    @Value("${educare.idempotency.trigger-window-seconds:30}")
    private long triggerIdempotencyWindowSeconds;

    private static final String LOCK_PREFIX = "agent:task:lock:";
    private static final String TRIGGER_IDEM_PREFIX = "edu:agent:trigger:";
    private static final long LOCK_EXPIRE_SECONDS = 120;

    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(
            TaskStatus.PENDING,
            TaskStatus.RISK_ANALYZING,
            TaskStatus.KNOWLEDGE_RETRIEVING,
            TaskStatus.PLAN_GENERATING,
            TaskStatus.COMPLIANCE_CHECKING
    );

    // ==================== 0. 查询接口 ====================

    @Override
    public AgentTask getTaskDetail(Long taskId) {
        return agentTaskMapper.selectById(taskId);
    }

    @Override
    public IPage<AgentTask> listTasks(long page, long size, String status, String riskLevel) {
        LambdaQueryWrapper<AgentTask> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(AgentTask::getStatus, TaskStatus.valueOf(status));
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            qw.eq(AgentTask::getRiskLevel, RiskLevel.valueOf(riskLevel));
        }
        qw.orderByDesc(AgentTask::getCreatedAt);
        return agentTaskMapper.selectPage(new Page<>(page, size), qw);
    }

    // ==================== 1. 任务创建 ====================

    /**
     * D 阶段：幂等触发。
     * 1) Redis SETNX 占位（窗口 = educare.idempotency.trigger-window-seconds，默认 30s）
     * 2) 命中则查询该 studentId 最近的活跃任务并返回，不创建新任务也不再触发执行
     * 3) 未命中则正常 createTask + asyncExecute，返回新 taskId
     */
    @Override
    public Long triggerTask(Long studentId) {
        String idemKey = TRIGGER_IDEM_PREFIX + studentId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idemKey, "1", triggerIdempotencyWindowSeconds, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(acquired)) {
            AgentTask existing = findLatestActiveByStudent(studentId);
            if (existing != null) {
                log.info("幂等命中：studentId={} 已有活跃任务 taskId={}，复用", studentId, existing.getId());
                return existing.getId();
            }
            log.info("幂等命中但未找到活跃任务，studentId={} 继续创建新任务", studentId);
        }

        Long taskId = createTask(studentId);
        asyncExecute(taskId);
        return taskId;
    }

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

    private AgentTask findLatestActiveByStudent(Long studentId) {
        return agentTaskMapper.selectOne(
                new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getStudentId, studentId)
                        .in(AgentTask::getStatus, ACTIVE_STATUSES)
                        .orderByDesc(AgentTask::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    /**
     * Attempts to execute the agent task identified by taskId while holding a distributed lock.
     *
     * Acquires a Redis-based lock for the given task id; if the lock is obtained, runs the task execution flow and always releases the lock. On execution failure the task is marked failed and a terminal event is published.
     *
     * @param taskId the id of the AgentTask to execute
     */

    @Override
    @Async("agentExecutor")
    public void asyncExecute(Long taskId) {
        String lockKey = LOCK_PREFIX + taskId;
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.info("分布式锁！");
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("任务 {} 正在执行中，跳过重复调度", taskId);
            return;
        }

        try {
            doExecute(taskId);
        } catch (Exception e) {
            log.error("任务 {} 执行异常", taskId, e);
            failTask(taskId);
            publishTerminal(taskId);
        } finally {
            log.info("释放锁！");
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey), lockValue);
        }
    }

    /**
     * Execute the task state machine for the given task id, advancing the task through risk analysis,
     * knowledge retrieval, plan generation, compliance checking, and finalization as appropriate.
     *
     * The method loads the task, validates it, and performs stage transitions with persisted updates:
     * - PENDING → RISK_ANALYZING: run risk analysis; short-circuit to COMPLETED if risk is NONE or LOW.
     * - RISK_ANALYZING → KNOWLEDGE_RETRIEVING: perform knowledge retrieval.
     * - KNOWLEDGE_RETRIEVING → PLAN_GENERATING: generate an intervention plan.
     * - PLAN_GENERATING → COMPLIANCE_CHECKING: perform compliance audit and transition to REJECTED if audit fails.
     * - COMPLIANCE_CHECKING → COMPLETED: finalize the task when all checks pass.
     *
     * Side effects: updates task fields in the database, performs state transitions via `transition(...)`,
     * writes completion/failure timestamps via `completeTask(...)`, and publishes terminal events via `publishTerminal(...)`.
     *
     * @param taskId the primary key of the AgentTask to execute
     */

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
                publishTerminal(taskId);
                log.info("任务 {} 风险等级 {}，直接完成", taskId, level);
                return;
            }
        }

        // ========== 阶段 2: RAG 检索 ==========
        if (task.getStatus() == TaskStatus.RISK_ANALYZING) {
            transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.KNOWLEDGE_RETRIEVING);
            task.setStatus(TaskStatus.KNOWLEDGE_RETRIEVING); // 同步内存，防止后续 updateById 回写

            String knowledge = executeKnowledgeRetrieve(task);
            task.setRetrievedKnowledge(knowledge);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getRetrievedKnowledge, knowledge));
        }

        // ========== 阶段 3: 方案生成 ==========
        if (task.getStatus() == TaskStatus.KNOWLEDGE_RETRIEVING) {
            transition(taskId, TaskStatus.KNOWLEDGE_RETRIEVING, TaskStatus.PLAN_GENERATING);
            task.setStatus(TaskStatus.PLAN_GENERATING);

            String plan = executePlanGenerate(task);
            task.setInterventionPlan(plan);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getInterventionPlan, plan));
        }

        // ========== 阶段 4: 合规审核 ==========
        if (task.getStatus() == TaskStatus.PLAN_GENERATING) {
            transition(taskId, TaskStatus.PLAN_GENERATING, TaskStatus.COMPLIANCE_CHECKING);
            task.setStatus(TaskStatus.COMPLIANCE_CHECKING);

            String audit = executeComplianceAudit(task);
            task.setComplianceAudit(audit);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getComplianceAudit, audit));

            boolean passed = parseAuditPassed(audit);
            if (!passed) {
                transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.REJECTED);
                publishTerminal(taskId);
                log.warn("任务 {} 合规审核未通过，转人工", taskId);
                return;
            }
        }

        // ========== 完成 ==========
        if (task.getStatus() == TaskStatus.COMPLIANCE_CHECKING) {
            transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.COMPLETED);
            completeTask(taskId);
            publishTerminal(taskId);
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
        String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(studentId));

        // ② 调用本地 llama.cpp
        return riskAnalyzeService.analyzeRisk(maskedProfile);
    }

    /**
     * 阶段 2：RAG 检索
     * 用风险类型 + 根因分析作为多路查询，调用 ai-inference-service 的 Milvus 召回。
     */
    private String executeKnowledgeRetrieve(AgentTask task) {
        try {
            JSONObject risk = JSON.parseObject(task.getRiskAnalysisResult());
            String riskType = risk == null ? null : risk.getString("primary_risk_type");
            String rootCause = risk == null ? null : risk.getString("root_cause_analysis");

            Map<String, Object> req = new HashMap<>();
            req.put("risk_type", riskType != null ? riskType : "综合风险");
            req.put("queries", List.of(
                    riskType != null ? riskType : "学生学业风险",
                    rootCause != null ? rootCause : "高校学生干预方法"
            ));
            req.put("top_k", 5);

            String response = aiInferenceClient.retrieveKnowledge(req);
            log.info("任务 {} RAG 检索完成，长度: {}", task.getId(), response == null ? 0 : response.length());
            return response != null ? response : fallbackKnowledge();
        } catch (Exception e) {
            log.error("任务 {} RAG 检索异常", task.getId(), e);
            return fallbackKnowledge();
        }
    }

    /**
     * Generate an intervention plan for the given task using the masked student profile, risk analysis, and retrieved knowledge.
     *
     * @param task the AgentTask containing the studentId, riskAnalysisResult, and retrievedKnowledge used to build the request
     * @return a JSON-formatted intervention plan as a String; returns a predefined fallback plan JSON when generation fails or no result is produced
     */
    private String executePlanGenerate(AgentTask task) {
        try {
            String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(task.getStudentId()));

            Map<String, Object> req = new HashMap<>();
            req.put("student_profile", JSON.parseObject(maskedProfile));
            req.put("risk_analysis", JSON.parseObject(task.getRiskAnalysisResult()));

            JSONObject knowledge = JSON.parseObject(task.getRetrievedKnowledge());
            JSONArray chunks = knowledge != null ? knowledge.getJSONArray("chunks") : null;
            req.put("knowledge_chunks", chunks != null ? chunks : Collections.emptyList());

            String response = aiInferenceClient.generatePlan(req);
            log.info("任务 {} 方案生成完成", task.getId());
            return response != null ? response : fallbackPlan();
        } catch (Exception e) {
            log.error("任务 {} 方案生成异常", task.getId(), e);
            return fallbackPlan();
        }
    }

    /**
     * Perform a compliance audit for the task's intervention plan.
     *
     * Uses the task's student profile (masked) and intervention plan to call the compliance audit service.
     *
     * @param task the task whose student profile and intervention plan are audited
     * @return a JSON string containing the audit result; on error returns a fallback audit JSON that has `audit_passed=false` and `manual_review_required=true`
     */
    private String executeComplianceAudit(AgentTask task) {
        try {
            String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(task.getStudentId()));

            Map<String, Object> req = new HashMap<>();
            req.put("student_profile", JSON.parseObject(maskedProfile));
            req.put("intervention_plan", JSON.parseObject(task.getInterventionPlan()));

            String response = aiInferenceClient.complianceAudit(req);
            log.info("任务 {} 合规审核完成", task.getId());
            return response != null ? response : fallbackAudit();
        } catch (Exception e) {
            log.error("任务 {} 合规审核异常", task.getId(), e);
            return fallbackAudit();
        }
    }

    private String fallbackKnowledge() {
        return "{\"chunks\":[],\"fallback\":true}";
    }

    private String fallbackPlan() {
        return "{\"report_title\":\"方案生成失败，转人工制定\",\"summary\":\"AI服务异常\","
                + "\"immediate_actions\":[],\"long_term_plan\":[],\"talk_outline\":\"请由辅导员主导\","
                + "\"resources\":[],\"references\":[]}";
    }

    private String fallbackAudit() {
        return "{\"audit_passed\":false,\"manual_review_required\":true,"
                + "\"audit_items\":[{\"dimension\":\"system\",\"passed\":false,\"issue\":\"审核服务异常\"}],"
                + "\"redacted_suggestions\":[\"请人工审核此方案\"]}";
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

    /**
     * Mark the task identified by the given id as failed in persistent storage.
     *
     * Sets the task's status to `FAILED` and writes the change to the database.
     *
     * @param taskId the id of the task to mark as failed
     */
    private void failTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(TaskStatus.FAILED);
        agentTaskMapper.updateById(update);
    }

    /**
     * Publish a terminal task-state event for the given task to subscribed clients.
     *
     * Re-queries the task to obtain its latest status and risk level before publishing;
     * if the task cannot be found the method returns without action. Any publishing errors
     * are caught and logged.
     *
     * @param taskId the identifier of the task whose terminal event should be published
     */
    private void publishTerminal(Long taskId) {
        try {
            AgentTask t = agentTaskMapper.selectById(taskId);
            if (t == null) return;
            String status = t.getStatus() == null ? "UNKNOWN" : t.getStatus().name();
            String riskLevel = t.getRiskLevel() == null ? null : t.getRiskLevel().name();
            warningPublisher.publishTerminal(taskId, status, riskLevel, t.getStudentId());
        } catch (Exception e) {
            log.warn("F-2：发布终态事件失败 taskId={} err={}", taskId, e.getMessage());
        }
    }

    /**
     * Parse the `risk_level` field from a JSON string into a `RiskLevel`.
     *
     * @param json the JSON string containing a `risk_level` field (case-insensitive)
     * @return the `RiskLevel` represented by the `risk_level` field; `RiskLevel.MEDIUM` if the field is missing, unrecognized, or parsing fails
     */

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

    /**
     * Determine whether a compliance audit result indicates the audit passed.
     *
     * @param json JSON string produced by the compliance audit, expected to contain the boolean field `audit_passed`
     * @return `true` if the `audit_passed` field is `true`, `false` otherwise (including when parsing fails)
     */
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
