package com.edu.mental.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Questionnaire;

public interface QuestionnaireService {

    Page<Questionnaire> list(Integer page, Integer size);

    Questionnaire getById(Long id);

    void save(Questionnaire questionnaire);

    void update(Questionnaire questionnaire);

    void delete(Long id);
}
