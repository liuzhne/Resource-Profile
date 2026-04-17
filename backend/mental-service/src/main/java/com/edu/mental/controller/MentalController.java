package com.edu.mental.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.fallback.MentalFallbackHandler;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mental")
@RequiredArgsConstructor
public class MentalController {

    private final QuestionnaireService questionnaireService;
    private final MentalAssessmentMapper mentalAssessmentMapper;

    @GetMapping("/overview")
    @SentinelResource(
            value = "mental_overview",
            blockHandlerClass = MentalFallbackHandler.class,
            blockHandler = "overviewBlockHandler",
            fallbackClass = MentalFallbackHandler.class,
            fallback = "overviewFallback"
    )
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> stats = mentalAssessmentMapper.selectRecentStats();
        result.put("recentStats", stats);

        result.put("goodRate", 85);
        result.put("attentionRate", 12);
        result.put("interventionRate", 3);
        result.put("todayCompleted", 89);

        return Result.success(result);
    }

    @GetMapping("/questionnaires")
    @SentinelResource(
            value = "mental_questionnaires",
            blockHandlerClass = MentalFallbackHandler.class,
            blockHandler = "listQuestionnairesBlockHandler",
            fallbackClass = MentalFallbackHandler.class,
            fallback = "listQuestionnairesFallback"
    )
    public Result<Page<Questionnaire>> listQuestionnaires(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Questionnaire> result = questionnaireService.list(page, size);
        return Result.success(result);
    }

    @GetMapping("/questionnaires/{id}")
    public Result<Questionnaire> getQuestionnaire(@PathVariable Long id) {
        Questionnaire questionnaire = questionnaireService.getById(id);
        return Result.success(questionnaire);
    }

    @PostMapping("/questionnaires")
    public Result<Void> saveQuestionnaire(@RequestBody Questionnaire questionnaire) {
        questionnaireService.save(questionnaire);
        return Result.success();
    }

    @PutMapping("/questionnaires/{id}")
    public Result<Void> updateQuestionnaire(@PathVariable Long id, @RequestBody Questionnaire questionnaire) {
        questionnaire.setId(id);
        questionnaireService.update(questionnaire);
        return Result.success();
    }

    @DeleteMapping("/questionnaires/{id}")
    public Result<Void> deleteQuestionnaire(@PathVariable Long id) {
        questionnaireService.delete(id);
        return Result.success();
    }

    @GetMapping("/analysis")
    @SentinelResource(
            value = "mental_analysis",
            blockHandlerClass = MentalFallbackHandler.class,
            blockHandler = "analysisBlockHandler",
            fallbackClass = MentalFallbackHandler.class,
            fallback = "analysisFallback"
    )
    public Result<Map<String, Object>> analysis() {
        Map<String, Object> result = new HashMap<>();

        result.put("deptDistribution", List.of(
                Map.of("dept", "计算机学院", "good", 120, "attention", 15, "intervention", 3),
                Map.of("dept", "数学学院", "good", 80, "attention", 10, "intervention", 2),
                Map.of("dept", "物理学院", "good", 90, "attention", 12, "intervention", 4)
        ));

        result.put("gradeComparison", List.of(
                Map.of("grade", "大一", "rate", 85),
                Map.of("grade", "大二", "rate", 82),
                Map.of("grade", "大三", "rate", 78),
                Map.of("grade", "大四", "rate", 75)
        ));

        result.put("focusGroups", List.of(
                Map.of("group", "学业困难学生", "count", 156, "risk", "中"),
                Map.of("group", "家庭经济困难学生", "count", 89, "risk", "中"),
                Map.of("group", "人际关系困扰学生", "count", 45, "risk", "高")
        ));

        return Result.success(result);
    }
}
