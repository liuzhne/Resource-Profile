package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {

    private final QuestionnaireMapper questionnaireMapper;

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
}
