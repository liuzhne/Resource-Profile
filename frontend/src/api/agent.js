import request from "@/utils/request";

export const triggerAgentTask = (studentId) =>
  request.post(`/agent/api/v1/task/trigger/${studentId}`);

export const getAgentTaskList = (params) =>
  request.get("/agent/api/v1/task/list", { params });

export const getAgentTaskDetail = (taskId) =>
  request.get(`/agent/api/v1/task/${taskId}`);
