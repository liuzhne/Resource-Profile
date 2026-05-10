import request from '@/utils/request'

export const triggerAgentTask = (studentId) =>
  request.post(`/agent/api/v1/task/trigger/${studentId}`)

export const getAgentTaskList = (params) =>
  request.get('/agent/api/v1/task/list', { params })

export const getAgentTaskDetail = (taskId) =>
  request.get(`/agent/api/v1/task/${taskId}`)

// F-1：PDF 报告异步导出
export const exportReport = (taskId) =>
  request.post(`/agent/api/v1/task/${taskId}/export`)

export const getExportStatus = (jobId) =>
  request.get(`/agent/api/v1/export/${jobId}`)

export const downloadExport = (jobId) =>
  request.get(`/agent/api/v1/export/${jobId}/download`, { responseType: 'blob' })
