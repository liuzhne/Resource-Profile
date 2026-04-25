package com.edu.agent.controller;

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
    public Result<Long> trigger(@PathVariable String studentId) {
        Long taskId = agentTaskService.createTask(studentId);
        agentTaskService.asyncExecute(taskId);
        return Result.success(taskId);
    }
}