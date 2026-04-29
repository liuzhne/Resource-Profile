package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<Questionnaire> list(Integer page, Integer size) {
        Page<Questionnaire> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Questionnaire> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Questionnaire::getCreateTime);
        return questionnaireMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Questionnaire getById(Long id) {
        return questionnaireMapper.selectById(id);
    }

    @Override
    public void save(Questionnaire questionnaire) {
        questionnaireMapper.insert(questionnaire);
    }

    @Override
    public void update(Questionnaire questionnaire) {
        questionnaireMapper.updateById(questionnaire);
    }

    @Override
    public void delete(Long id) {
        questionnaireMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Questionnaire saveFromTemplate(String templateJson) {
        try {
            Map<String, Object> template = objectMapper.readValue(templateJson,
                    new TypeReference<Map<String, Object>>() {});

            Questionnaire questionnaire = new Questionnaire();
            questionnaire.setTitle((String) template.get("title"));
            questionnaire.setType((String) template.get("type"));
            questionnaire.setDescription((String) template.get("description"));
            questionnaire.setTemplateContent(templateJson);

            if (template.get("startTime") != null) {
                questionnaire.setStartTime(LocalDate.parse((String) template.get("startTime")));
            }
            if (template.get("endTime") != null) {
                questionnaire.setEndTime(LocalDate.parse((String) template.get("endTime")));
            }

            questionnaire.setStatus(1);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionTemplates = (List<Map<String, Object>>) template.get("questions");
            int questionCount = questionTemplates != null ? questionTemplates.size() : 0;
            questionnaire.setQuestions(questionCount);

            questionnaireMapper.insert(questionnaire);

            if (questionTemplates != null && !questionTemplates.isEmpty()) {
                List<Question> questions = new ArrayList<>();
                for (Map<String, Object> qt : questionTemplates) {
                    Question question = new Question();
                    question.setSortOrder(qt.get("sortOrder") != null ? ((Number) qt.get("sortOrder")).intValue() : 0);
                    question.setContent((String) qt.get("content"));
                    question.setQuestionType((String) qt.get("questionType"));

                    if (qt.get("options") != null) {
                        question.setOptions(objectMapper.writeValueAsString(qt.get("options")));
                    }
                    if (qt.get("scoringRules") != null) {
                        question.setScoringRules(objectMapper.writeValueAsString(qt.get("scoringRules")));
                    }
                    if (qt.get("scaleMin") != null) {
                        question.setScaleMin(((Number) qt.get("scaleMin")).intValue());
                    }
                    if (qt.get("scaleMax") != null) {
                        question.setScaleMax(((Number) qt.get("scaleMax")).intValue());
                    }
                    if (qt.get("scaleLabels") != null) {
                        question.setScaleLabels(objectMapper.writeValueAsString(qt.get("scaleLabels")));
                    }
                    question.setRequired(qt.get("required") != null && Boolean.TRUE.equals(qt.get("required")) ? 1 : 0);

                    questions.add(question);
                }
                questionService.saveBatch(questionnaire.getId(), questions);
            }

            return questionnaire;
        } catch (Exception e) {
            log.error("Failed to parse questionnaire template", e);
            throw new RuntimeException("问卷模板解析失败: " + e.getMessage());
        }
    }
}
