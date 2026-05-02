package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.QuestionnaireResponse;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireResponseMapper;
import com.edu.mental.service.QuestionnaireResponseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireResponseServiceImpl implements QuestionnaireResponseService {

    private final QuestionnaireResponseMapper responseMapper;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void submit(QuestionnaireResponse response) {
        int score = computeScore(response.getQuestionnaireId(), response.getAnswers());
        response.setScore(score);
        response.setStatus(0);
        responseMapper.insert(response);
    }

    @Override
    public QuestionnaireResponse getByStudentAndQuestionnaire(Long studentId, Long questionnaireId) {
        LambdaQueryWrapper<QuestionnaireResponse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionnaireResponse::getStudentId, studentId)
               .eq(QuestionnaireResponse::getQuestionnaireId, questionnaireId);
        return responseMapper.selectOne(wrapper);
    }

    @Override
    public boolean hasSubmitted(Long studentId, Long questionnaireId) {
        return getByStudentAndQuestionnaire(studentId, questionnaireId) != null;
    }

    private int computeScore(Long questionnaireId, String answersJson) {
        try {
            Map<String, Object> answers = objectMapper.readValue(answersJson,
                    new TypeReference<Map<String, Object>>() {});

            LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Question::getQuestionnaireId, questionnaireId)
                   .orderByAsc(Question::getSortOrder);
            List<Question> questions = questionMapper.selectList(wrapper);

            int totalScore = 0;
            for (Question q : questions) {
                if (q.getScoringRules() == null || q.getScoringRules().isEmpty()) {
                    continue;
                }
                Map<String, Object> rules = objectMapper.readValue(q.getScoringRules(),
                        new TypeReference<Map<String, Object>>() {});
                Object answer = answers.get(String.valueOf(q.getId()));
                if (answer == null) {
                    continue;
                }
                if (answer instanceof List) {
                    for (Object item : (List<?>) answer) {
                        Object ruleScore = rules.get(String.valueOf(item));
                        if (ruleScore != null) {
                            totalScore += ((Number) ruleScore).intValue();
                        }
                    }
                } else {
                    Object ruleScore = rules.get(String.valueOf(answer));
                    if (ruleScore != null) {
                        totalScore += ((Number) ruleScore).intValue();
                    }
                }
            }
            return totalScore;
        } catch (Exception e) {
            log.error("Failed to compute score", e);
            return 0;
        }
    }
}
