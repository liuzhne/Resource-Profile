package com.edu.mental.controller;

import com.edu.common.result.Result;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学生侧问卷接口（按 userId 鉴权学生身份） */
@RestController
@RequestMapping("/mental/student")
@RequiredArgsConstructor
public class StudentMentalController {

    private final MentalAssessmentService assessmentService;
    private final QuestionService questionService;

    @GetMapping("/questionnaires")
    public Result<List<Map<String, Object>>> myQuestionnaires(@RequestParam Long userId) {
        return Result.success(assessmentService.listForStudent(userId));
    }

    @GetMapping("/questionnaires/{id}")
    public Result<QuestionnaireFullDto> getForTaking(@PathVariable Long id) {
        return Result.success(questionService.getFull(id));
    }

    @PostMapping("/assessments")
    public Result<MentalAssessment> submit(@RequestBody SubmitAnswerRequest req) {
        return Result.success(assessmentService.submit(req));
    }

    @GetMapping("/assessments")
    public Result<List<MentalAssessment>> myHistory(@RequestParam Long userId) {
        return Result.success(assessmentService.myHistory(userId));
    }

    @GetMapping("/assessments/{assessmentId}")
    public Result<Map<String, Object>> myDetail(
            @RequestParam Long userId,
            @PathVariable Long assessmentId) {
        return Result.success(assessmentService.getMyAssessmentDetail(userId, assessmentId));
    }
}
