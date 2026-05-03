package com.edu.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.edu.agent.entity.AgentTask;

public interface AgentTaskService {

    /**
     * 创建任务（同步）
     */
    Long createTask(Long studentId);

    /**
     * 异步执行 4-Agent 流水线
     */
    void asyncExecute(Long taskId);

    /**
     * 任务详情
     */
    AgentTask getTaskDetail(Long taskId);

    /**
     * 任务分页列表
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param status    状态过滤（可空）
     * @param riskLevel 风险等级过滤（可空）
     */
    IPage<AgentTask> listTasks(long page, long size, String status, String riskLevel);
}