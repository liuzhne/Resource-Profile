package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.mental.entity.Question;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;

    @Override
    public List<Question> listByQuestionnaireId(Long questionnaireId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionnaireId, questionnaireId)
               .orderByAsc(Question::getSortOrder);
        return questionMapper.selectList(wrapper);
    }

    @Override
    public void saveBatch(Long questionnaireId, List<Question> questions) {
        for (Question q : questions) {
            q.setQuestionnaireId(questionnaireId);
            questionMapper.insert(q);
        }
    }

    @Override
    public void deleteByQuestionnaireId(Long questionnaireId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionnaireId, questionnaireId);
        questionMapper.delete(wrapper);
    }
}
