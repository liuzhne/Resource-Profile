package com.edu.agent.service;

public interface AgentTaskService {

    /**
     * 创建任务（同步）
     */
    Long createTask(String studentId);

    /**
     * 异步执行 4-Agent 流水线
     */
    void asyncExecute(Long taskId);
}