package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

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

    @Override
    @Transactional
    public void importQuestionsFromExcel(Long questionnaireId, MultipartFile file) {
        Questionnaire questionnaire = questionnaireMapper.selectById(questionnaireId);
        if (questionnaire == null) {
            throw new RuntimeException("问卷不存在");
        }

        List<Question> questions = parseExcel(file);
        if (questions.isEmpty()) {
            throw new RuntimeException("Excel文件中没有有效题目");
        }

        // 先删除该问卷已有题目
        questionService.deleteByQuestionnaireId(questionnaireId);

        // 批量保存新题目
        questionService.saveBatch(questionnaireId, questions);

        // 更新问卷题目数
        questionnaire.setQuestions(questions.size());
        questionnaireMapper.updateById(questionnaire);
    }

    private List<Question> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Question> questions = new ArrayList<>();

            // 跳过表头行，从第二行开始读取
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || getCellStringValue(row.getCell(0)).isEmpty()) {
                    continue;
                }

                Question question = new Question();

                // 题号
                String sortOrderStr = getCellStringValue(row.getCell(0));
                try {
                    question.setSortOrder(Integer.parseInt(sortOrderStr));
                } catch (NumberFormatException e) {
                    question.setSortOrder(i);
                }

                // 题目内容
                question.setContent(getCellStringValue(row.getCell(1)));
                if (question.getContent().isEmpty()) {
                    continue;
                }

                // 题目类型
                String questionType = getCellStringValue(row.getCell(2));
                question.setQuestionType(normalizeQuestionType(questionType));

                // 选项和分值 (choice types)
                if ("single_choice".equals(question.getQuestionType()) || "multiple_choice".equals(question.getQuestionType())) {
                    List<Map<String, Object>> options = new ArrayList<>();
                    Map<String, Object> scoringRules = new LinkedHashMap<>();

                    String[] optionValues = {"A", "B", "C", "D", "E", "F"};
                    for (int j = 0; j < 6; j++) {
                        String optionText = getCellStringValue(row.getCell(3 + j));
                        if (!optionText.isEmpty()) {
                            Map<String, Object> opt = new LinkedHashMap<>();
                            opt.put("label", optionText);
                            opt.put("value", optionValues[j]);
                            options.add(opt);

                            // 对应分值
                            String scoreStr = getCellStringValue(row.getCell(9 + j));
                            if (!scoreStr.isEmpty()) {
                                try {
                                    scoringRules.put(optionValues[j], Integer.parseInt(scoreStr));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }

                    if (!options.isEmpty()) {
                        question.setOptions(toJson(options));
                    }
                    if (!scoringRules.isEmpty()) {
                        question.setScoringRules(toJson(scoringRules));
                    }
                }

                // 量表类型: 选项A 为范围 "1~10", 选项B=min标签, 选项C=max标签
                if ("scale".equals(question.getQuestionType())) {
                    String range = getCellStringValue(row.getCell(3));
                    if (!range.isEmpty() && range.contains("~")) {
                        String[] parts = range.split("~");
                        try {
                            question.setScaleMin(Integer.parseInt(parts[0].trim()));
                            question.setScaleMax(Integer.parseInt(parts[1].trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    String minLabel = getCellStringValue(row.getCell(4));
                    String maxLabel = getCellStringValue(row.getCell(5));
                    if (!minLabel.isEmpty() || !maxLabel.isEmpty()) {
                        Map<String, String> labels = new LinkedHashMap<>();
                        labels.put("min", minLabel);
                        labels.put("max", maxLabel);
                        question.setScaleLabels(toJson(labels));
                    }
                }

                // 是否必答
                String requiredStr = getCellStringValue(row.getCell(15));
                question.setRequired("否".equals(requiredStr) || "N".equalsIgnoreCase(requiredStr) || "0".equals(requiredStr) ? 0 : 1);

                questions.add(question);
            }

            return questions;
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage());
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isEmpty()) {
            return "single_choice";
        }
        return switch (type) {
            case "单选", "single_choice" -> "single_choice";
            case "多选", "multiple_choice" -> "multiple_choice";
            case "文本", "填空", "text" -> "text";
            case "量表", "scale" -> "scale";
            default -> "single_choice";
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }
}
