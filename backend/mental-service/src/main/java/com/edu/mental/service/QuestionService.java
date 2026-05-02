package com.edu.mental.service;

import com.edu.mental.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> listByQuestionnaireId(Long questionnaireId);

    void saveBatch(Long questionnaireId, List<Question> questions);

    void deleteByQuestionnaireId(Long questionnaireId);
}
