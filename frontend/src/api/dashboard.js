import request from '@/utils/request'

export const getStatistics = () => {
  return request.get('/data/dashboard/statistics')
}

export const getTrendData = () => {
  return request.get('/data/dashboard/trend')
}

export const getDistributionData = () => {
  return request.get('/data/dashboard/distribution')
}

export const getRecentLogins = () => {
  return request.get('/data/dashboard/recentLogins')
}
