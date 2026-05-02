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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
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
        List<Questionnaire> list = questionnaireService.list(1, 100).getRecords();
        List<Questionnaire> filtered = list.stream()
                .filter(q -> q.getStatus() == 1
                        && q.getStartTime() != null && !q.getStartTime().isAfter(LocalDate.now())
                        && q.getEndTime() != null && !q.getEndTime().isBefore(LocalDate.now()))
                .collect(Collectors.toList());
        return Result.success(filtered);
    }

    @GetMapping("/questionnaires/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("问卷题目");

            // 表头
            String[] headers = {"题号", "题目内容", "题目类型", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                    "A分值", "B分值", "C分值", "D分值", "E分值", "F分值", "是否必答"};
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 16 * 256);
            }

            // 示例行1: 单选题
            Row example1 = sheet.createRow(1);
            example1.createCell(0).setCellValue(1);
            example1.createCell(1).setCellValue("我觉得比平常容易紧张和着急");
            example1.createCell(2).setCellValue("单选");
            example1.createCell(3).setCellValue("A.没有或很少时间");
            example1.createCell(4).setCellValue("B.少部分时间");
            example1.createCell(5).setCellValue("C.相当多时间");
            example1.createCell(6).setCellValue("D.绝大部分或全部时间");
            example1.createCell(9).setCellValue(1);
            example1.createCell(10).setCellValue(2);
            example1.createCell(11).setCellValue(3);
            example1.createCell(12).setCellValue(4);
            example1.createCell(15).setCellValue("是");

            // 示例行2: 多选题
            Row example2 = sheet.createRow(2);
            example2.createCell(0).setCellValue(2);
            example2.createCell(1).setCellValue("您出现以下哪些情况？（多选）");
            example2.createCell(2).setCellValue("多选");
            example2.createCell(3).setCellValue("A.失眠");
            example2.createCell(4).setCellValue("B.食欲下降");
            example2.createCell(5).setCellValue("C.注意力不集中");
            example2.createCell(6).setCellValue("D.以上都没有");
            example2.createCell(9).setCellValue(2);
            example2.createCell(10).setCellValue(2);
            example2.createCell(11).setCellValue(1);
            example2.createCell(12).setCellValue(0);
            example2.createCell(15).setCellValue("是");

            // 示例行3: 文本题
            Row example3 = sheet.createRow(3);
            example3.createCell(0).setCellValue(3);
            example3.createCell(1).setCellValue("请描述最近让您感到焦虑的一件事");
            example3.createCell(2).setCellValue("文本");
            example3.createCell(15).setCellValue("否");

            // 示例行4: 量表题
            Row example4 = sheet.createRow(4);
            example4.createCell(0).setCellValue(4);
            example4.createCell(1).setCellValue("您对目前生活状态的满意程度");
            example4.createCell(2).setCellValue("量表");
            example4.createCell(3).setCellValue("1~10");
            example4.createCell(4).setCellValue("非常不满意");
            example4.createCell(5).setCellValue("非常满意");
            example4.createCell(15).setCellValue("是");

            // 说明 Sheet
            Sheet instrSheet = workbook.createSheet("填写说明");
            instrSheet.setColumnWidth(0, 80 * 256);
            String[] instructions = {
                "【问卷模板填写说明】",
                "",
                "1. 题目类型可选值：单选、多选、文本、量表",
                "2. 单选/多选题：在选项A~F列填写选项文本，对应分值列填写该选项的分值（数字）",
                "3. 文本题：无需填写选项和分值",
                "4. 量表题：选项A列填写范围（如 1~10），选项B列填写最小值标签，选项C列填写最大值标签",
                "5. 是否必答填写：是 或 否",
                "6. 请勿修改表头行，从第二行开始填写题目",
                "7. 题号决定题目显示顺序"
            };
            for (int i = 0; i < instructions.length; i++) {
                instrSheet.createRow(i).createCell(0).setCellValue(instructions[i]);
            }

            workbook.write(out);
            byte[] bytes = out.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=questionnaire_template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
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

    @PostMapping("/questionnaires/{id}/upload")
    public Result<Void> uploadQuestionnaireQuestions(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            questionnaireService.importQuestionsFromExcel(id, file);
            return Result.success();
        } catch (Exception e) {
            return Result.error("问卷题目上传失败: " + e.getMessage());
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
