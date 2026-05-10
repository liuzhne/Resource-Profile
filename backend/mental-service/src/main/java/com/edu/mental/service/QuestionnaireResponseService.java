package com.edu.mental.service;

import com.edu.mental.entity.QuestionnaireResponse;

public interface QuestionnaireResponseService {

    void submit(QuestionnaireResponse response);

    QuestionnaireResponse getByStudentAndQuestionnaire(Long studentId, Long questionnaireId);

    boolean hasSubmitted(Long studentId, Long questionnaireId);
}
