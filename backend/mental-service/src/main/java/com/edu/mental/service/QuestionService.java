package com.edu.mental.service;

import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> listByQuestionnaire(Long questionnaireId);

    QuestionnaireFullDto getFull(Long questionnaireId);

    Question save(Question question);

    void saveBatch(Long questionnaireId, List<Question> questions);

    void update(Question question);

    void delete(Long questionId);

    void deleteByQuestionnaireId(Long questionnaireId);
}
