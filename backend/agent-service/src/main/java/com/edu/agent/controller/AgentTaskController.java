package com.edu.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent/api/v1/task")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    /**
     * 手动触发单学生分析
     */
    @PostMapping("/trigger/{studentId}")
    public Result<Long> trigger(@PathVariable Long studentId) {
        Long taskId = agentTaskService.createTask(studentId);
        agentTaskService.asyncExecute(taskId);
        return Result.success(taskId);
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
