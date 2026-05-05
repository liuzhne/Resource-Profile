package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mental")
@RequiredArgsConstructor
public class MentalController {

    private final QuestionnaireService questionnaireService;
    private final QuestionService questionService;
    private final MentalAssessmentService assessmentService;
    private final MentalAssessmentMapper mentalAssessmentMapper;

    /* ========== 心理概览 / 分析报告（保留原有逻辑） ========== */

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> stats = mentalAssessmentMapper.selectRecentStats();
        result.put("recentStats", stats);

        int goodCount = 0, attentionCount = 0, interventionCount = 0, totalCount = 0;
        for (Map<String, Object> stat : stats) {
            String level = (String) stat.get("level");
            int count = ((Number) stat.get("count")).intValue();
            totalCount += count;
            if ("正常".equals(level) || "轻度".equals(level)) goodCount += count;
            else if ("中度".equals(level)) attentionCount += count;
            else if ("重度".equals(level) || "高危".equals(level)) interventionCount += count;
        }
        int goodRate = totalCount > 0 ? (goodCount * 100 / totalCount) : 0;
        int attentionRate = totalCount > 0 ? (attentionCount * 100 / totalCount) : 0;
        int interventionRate = totalCount > 0 ? (interventionCount * 100 / totalCount) : 0;
        result.put("goodRate", goodRate);
        result.put("attentionRate", attentionRate);
        result.put("interventionRate", interventionRate);
        result.put("todayCompleted", totalCount);
        result.put("warningList", mentalAssessmentMapper.selectWarningList());
        result.put("trendData", mentalAssessmentMapper.selectTrendData());
        return Result.success(result);
    }

    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis() {
        Map<String, Object> result = new HashMap<>();
        result.put("deptDistribution", mentalAssessmentMapper.selectDeptDistribution());
        result.put("gradeComparison", mentalAssessmentMapper.selectGradeComparison());
        result.put("focusGroups", mentalAssessmentMapper.selectFocusGroups());
        result.put("genderAnalysis", mentalAssessmentMapper.selectGenderAnalysis());
        return Result.success(result);
    }

    /* ========== 问卷元数据 CRUD ========== */

    @GetMapping("/questionnaires")
    public Result<Page<Questionnaire>> listQuestionnaires(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(questionnaireService.list(page, size));
    }

    @GetMapping("/questionnaires/{id}")
    public Result<Questionnaire> getQuestionnaire(@PathVariable Long id) {
        return Result.success(questionnaireService.getById(id));
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

    /* ========== 问卷完整内容（含题目+等级规则）：设计/预览复用 ========== */

    @GetMapping("/questionnaires/{id}/full")
    public Result<QuestionnaireFullDto> getFull(@PathVariable Long id) {
        return Result.success(questionService.getFull(id));
    }

    /* ========== 题目 CRUD（教师/管理员） ========== */

    @GetMapping("/questionnaires/{id}/questions")
    public Result<List<Question>> listQuestions(@PathVariable Long id) {
        return Result.success(questionService.listByQuestionnaire(id));
    }

    @PostMapping("/questionnaires/{id}/questions")
    public Result<Question> addQuestion(@PathVariable Long id, @RequestBody Question question) {
        question.setQuestionnaireId(id);
        return Result.success(questionService.save(question));
    }

    @PutMapping("/questions/{questionId}")
    public Result<Void> updateQuestion(@PathVariable Long questionId, @RequestBody Question question) {
        question.setId(questionId);
        questionService.update(question);
        return Result.success();
    }

    @DeleteMapping("/questions/{questionId}")
    public Result<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.delete(questionId);
        return Result.success();
    }

    /* ========== 完成情况（教师/管理员看哪些学生答了） ========== */

    @GetMapping("/questionnaires/{id}/completion")
    public Result<List<Map<String, Object>>> completion(@PathVariable Long id) {
        return Result.success(assessmentService.listCompletion(id));
    }
}
