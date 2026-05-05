package com.edu.agent.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/agent/api/v1/task")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    /**
     * 手动触发单学生分析。
     * D 阶段保护：
     *  - Sentinel QPS 限流（资源名 agent:trigger）
     *  - 服务层 Redis 幂等：同一 studentId 30s 内复用最近活跃任务
     */
    @PostMapping("/trigger/{studentId}")
    @SentinelResource(value = "agent:trigger", blockHandler = "triggerBlocked")
    public Result<Long> trigger(@PathVariable Long studentId) {
        Long taskId = agentTaskService.triggerTask(studentId);
        return Result.success(taskId);
    }

    /** Sentinel 限流降级：方法签名必须与原方法一致并多接 BlockException。 */
    public Result<Long> triggerBlocked(Long studentId, BlockException ex) {
        log.warn("trigger 被限流：studentId={}, rule={}", studentId, ex.getRuleLimitApp());
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    /**
     * 任务详情（含 4 阶段产物 JSON）
     */
    @GetMapping("/{taskId}")
    public Result<AgentTask> detail(@PathVariable Long taskId) {
        AgentTask task = agentTaskService.getTaskDetail(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 任务分页列表（前端 AI 预警中心用）
     */
    @GetMapping("/list")
    public Result<IPage<AgentTask>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {
        return Result.success(agentTaskService.listTasks(page, size, status, riskLevel));
    }
}
