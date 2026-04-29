package com.edu.mental.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.entity.QuestionnaireResponse;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireResponseService;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mental")
@RequiredArgsConstructor
public class MentalController {

    private final QuestionnaireService questionnaireService;
    private final QuestionService questionService;
    private final QuestionnaireResponseService questionnaireResponseService;
    private final MentalAssessmentMapper mentalAssessmentMapper;

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
            if ("正常".equals(level) || "轻度".equals(level)) {
                goodCount += count;
            } else if ("中度".equals(level)) {
                attentionCount += count;
            } else if ("重度".equals(level) || "高危".equals(level)) {
                interventionCount += count;
            }
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

    @GetMapping("/questionnaires")
    public Result<Page<Questionnaire>> listQuestionnaires(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Questionnaire> result = questionnaireService.list(page, size);
        return Result.success(result);
    }

    @GetMapping("/questionnaires/available")
    public Result<List<Questionnaire>> listAvailable() {
        LambdaQueryWrapper<Questionnaire> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Questionnaire::getStatus, 1)
               .le(Questionnaire::getStartTime, LocalDate.now())
               .ge(Questionnaire::getEndTime, LocalDate.now())
               .orderByDesc(Questionnaire::getCreateTime);
        List<Questionnaire> list = questionnaireService.list(1, 100).getRecords();
        List<Questionnaire> filtered = list.stream()
                .filter(q -> q.getStatus() == 1
                        && q.getStartTime() != null && !q.getStartTime().isAfter(LocalDate.now())
                        && q.getEndTime() != null && !q.getEndTime().isBefore(LocalDate.now()))
                .collect(Collectors.toList());
        return Result.success(filtered);
    }

    @GetMapping("/questionnaires/{id}")
    public Result<Questionnaire> getQuestionnaire(@PathVariable Long id) {
        Questionnaire questionnaire = questionnaireService.getById(id);
        return Result.success(questionnaire);
    }

    @GetMapping("/questionnaires/{id}/questions")
    public Result<List<Question>> getQuestionnaireQuestions(@PathVariable Long id) {
        List<Question> questions = questionService.listByQuestionnaireId(id);
        return Result.success(questions);
    }

    @PostMapping("/questionnaires")
    public Result<Void> saveQuestionnaire(@RequestBody Questionnaire questionnaire) {
        questionnaireService.save(questionnaire);
        return Result.success();
    }

    @PostMapping("/questionnaires/upload")
    public Result<Questionnaire> uploadQuestionnaire(@RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            Questionnaire questionnaire = questionnaireService.saveFromTemplate(content);
            return Result.success(questionnaire);
        } catch (Exception e) {
            return Result.error("问卷上传失败: " + e.getMessage());
        }
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

    @PostMapping("/responses")
    public Result<Void> submitResponse(@RequestBody QuestionnaireResponse response) {
        questionnaireResponseService.submit(response);
        return Result.success();
    }

    @GetMapping("/responses/check")
    public Result<Map<String, Object>> checkResponse(
            @RequestParam Long studentId,
            @RequestParam Long questionnaireId) {
        Map<String, Object> result = new HashMap<>();
        boolean submitted = questionnaireResponseService.hasSubmitted(studentId, questionnaireId);
        result.put("submitted", submitted);
        if (submitted) {
            QuestionnaireResponse response = questionnaireResponseService.getByStudentAndQuestionnaire(studentId, questionnaireId);
            result.put("response", response);
        }
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
}
