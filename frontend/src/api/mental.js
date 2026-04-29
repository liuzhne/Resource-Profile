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

export const uploadQuestionnaireTemplate = (formData) => {
  return request.post('/mental/questionnaires/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const getQuestionnaireQuestions = (id) => {
  return request.get(`/mental/questionnaires/${id}/questions`)
}

export const getAvailableQuestionnaires = () => {
  return request.get('/mental/questionnaires/available')
}

export const submitQuestionnaireResponse = (data) => {
  return request.post('/mental/responses', data)
}

export const checkQuestionnaireResponse = (studentId, questionnaireId) => {
  return request.get('/mental/responses/check', {
    params: { studentId, questionnaireId }
  })
}

export const getMentalAnalysis = () => {
  return request.get('/mental/analysis')
}
