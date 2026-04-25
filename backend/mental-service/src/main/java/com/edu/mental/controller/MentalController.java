package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mental")
@RequiredArgsConstructor
public class MentalController {

    private final QuestionnaireService questionnaireService;
    private final MentalAssessmentMapper mentalAssessmentMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new HashMap<>();

        // 统计近30天的心理健康数据
        List<Map<String, Object>> stats = mentalAssessmentMapper.selectRecentStats();
        result.put("recentStats", stats);

        // 从数据库结果计算总体统计
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

        // 预警列表
        result.put("warningList", mentalAssessmentMapper.selectWarningList());

        // 趋势数据
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
    public Result<Map<String, Object>> analysis() {
        Map<String, Object> result = new HashMap<>();

        // 各学院分布 — 从数据库查询
        result.put("deptDistribution", mentalAssessmentMapper.selectDeptDistribution());

        // 年级对比 — 从数据库查询
        result.put("gradeComparison", mentalAssessmentMapper.selectGradeComparison());

        // 重点人群 — 从数据库查询
        result.put("focusGroups", mentalAssessmentMapper.selectFocusGroups());

        // 性别差异 — 从数据库查询
        result.put("genderAnalysis", mentalAssessmentMapper.selectGenderAnalysis());

        return Result.success(result);
    }
}
