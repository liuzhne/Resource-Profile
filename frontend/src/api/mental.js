import request from '@/utils/request'

export const getMentalOverview = () => {
  return request.get('/mental/overview')
}

export const getQuestionnaireList = (params) => {
  return request.get('/mental/questionnaires', { params })
}

export const getQuestionnaireDetail = (id) => {
  return request.get(`/mental/questionnaires/${id}`)
}

export const createQuestionnaire = (data) => {
  return request.post('/mental/questionnaires', data)
}

export const updateQuestionnaire = (id, data) => {
  return request.put(`/mental/questionnaires/${id}`, data)
}

export const deleteQuestionnaire = (id) => {
  return request.delete(`/mental/questionnaires/${id}`)
}

export const getMentalAnalysis = () => {
  return request.get('/mental/analysis')
}
