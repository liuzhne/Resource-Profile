package com.edu.mental.dto;

import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import lombok.Data;

import java.util.List;

@Data
public class QuestionnaireFullDto {
    private Questionnaire questionnaire;
    private List<Question> questions;
    private List<LevelRule> levelRules;
}
