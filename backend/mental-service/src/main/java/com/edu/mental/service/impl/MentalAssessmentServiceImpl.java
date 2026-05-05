package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.mental.dto.LevelRule;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.entity.MentalResponse;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.mapper.MentalResponseMapper;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.mapper.StudentLookupMapper;
import com.edu.mental.service.MentalAssessmentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentalAssessmentServiceImpl implements MentalAssessmentService {

    private static final String TYPE_SINGLE = "single_choice";
    private static final String TYPE_MULTI = "multiple_choice";
    private static final String TYPE_TEXT = "text";

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionMapper questionMapper;
    private final MentalResponseMapper responseMapper;
    private final MentalAssessmentMapper assessmentMapper;
    private final StudentLookupMapper studentLookupMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> listForStudent(Long userId) {
        Long studentId = resolveStudentId(userId);

        LambdaQueryWrapper<Questionnaire> w = new LambdaQueryWrapper<>();
        w.eq(Questionnaire::getStatus, 1).orderByDesc(Questionnaire::getCreateTime);
        List<Questionnaire> all = questionnaireMapper.selectList(w);
        LocalDate today = LocalDate.now();

        Set<Long> answered = new HashSet<>();
        if (studentId != null) {
            List<MentalResponse> done = responseMapper.selectList(
                    new LambdaQueryWrapper<MentalResponse>().eq(MentalResponse::getStudentId, studentId)
            );
            done.forEach(r -> answered.add(r.getQuestionnaireId()));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Questionnaire q : all) {
            boolean inWindow =
                    (q.getStartTime() == null || !today.isBefore(q.getStartTime())) &&
                    (q.getEndTime() == null   || !today.isAfter(q.getEndTime()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.getId());
            row.put("title", q.getTitle());
            row.put("type", q.getType());
            row.put("description", q.getDescription());
            row.put("questions", q.getQuestions());
            row.put("startTime", q.getStartTime());
            row.put("endTime", q.getEndTime());
            row.put("inWindow", inWindow);
            row.put("answered", answered.contains(q.getId()));
            out.add(row);
        }
        return out;
    }

    @Override
    @Transactional
    public MentalAssessment submit(SubmitAnswerRequest req) {
        if (req.getUserId() == null || req.getQuestionnaireId() == null || req.getAnswers() == null) {
            throw new RuntimeException("提交参数不完整");
        }
        Long studentId = resolveStudentId(req.getUserId());
        if (studentId == null) {
            throw new RuntimeException("当前用户未关联学生信息");
        }

        Questionnaire q = questionnaireMapper.selectById(req.getQuestionnaireId());
        if (q == null) {
            throw new RuntimeException("问卷不存在");
        }
        if (q.getStatus() == null || q.getStatus() != 1) {
            throw new RuntimeException("问卷当前未开放");
        }
        LocalDate today = LocalDate.now();
        if (q.getStartTime() != null && today.isBefore(q.getStartTime())) {
            throw new RuntimeException("问卷尚未开始");
        }
        if (q.getEndTime() != null && today.isAfter(q.getEndTime())) {
            throw new RuntimeException("问卷已结束");
        }

        Long existingResponseCount = responseMapper.selectCount(
                new LambdaQueryWrapper<MentalResponse>()
                        .eq(MentalResponse::getStudentId, studentId)
                        .eq(MentalResponse::getQuestionnaireId, q.getId()));
        if (existingResponseCount > 0) {
            throw new RuntimeException("您已提交过该问卷");
        }

        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getQuestionnaireId, q.getId())
                        .orderByAsc(Question::getSortOrder)
                        .orderByAsc(Question::getId));
        Map<Long, Question> questionMap = new HashMap<>();
        questions.forEach(qq -> questionMap.put(qq.getId(), qq));

        int totalScore = 0;
        for (SubmitAnswerRequest.AnswerItem ans : req.getAnswers()) {
            Question qi = questionMap.get(ans.getQuestionId());
            if (qi == null) continue;
            totalScore += scoreForAnswer(qi, ans);
        }

        MentalResponse resp = new MentalResponse();
        resp.setStudentId(studentId);
        resp.setQuestionnaireId(q.getId());
        resp.setScore(totalScore);
        resp.setStatus(1);
        try {
            resp.setAnswers(objectMapper.writeValueAsString(req.getAnswers()));
        } catch (Exception e) {
            throw new RuntimeException("答卷序列化失败: " + e.getMessage());
        }
        responseMapper.insert(resp);

        LevelRule rule = pickLevelRule(parseLevelRules(q.getLevelRules()), totalScore);
        MentalAssessment assessment = new MentalAssessment();
        assessment.setStudentId(studentId);
        assessment.setQuestionnaireId(q.getId());
        assessment.setScore(totalScore);
        assessment.setLevel(rule != null ? rule.getLevel() : "未评级");
        assessment.setSuggestion(rule != null ? rule.getSuggestion() : null);
        assessment.setResult("总分 " + totalScore + "，评级：" + (rule != null ? rule.getLevel() : "未评级"));
        assessment.setStatus(1);
        assessmentMapper.insert(assessment);
        return assessment;
    }

    @Override
    public List<MentalAssessment> myHistory(Long userId) {
        Long studentId = resolveStudentId(userId);
        if (studentId == null) return Collections.emptyList();
        return assessmentMapper.selectList(
                new LambdaQueryWrapper<MentalAssessment>()
                        .eq(MentalAssessment::getStudentId, studentId)
                        .orderByDesc(MentalAssessment::getCreateTime));
    }

    @Override
    public Map<String, Object> getMyAssessmentDetail(Long userId, Long assessmentId) {
        Long studentId = resolveStudentId(userId);
        MentalAssessment a = assessmentMapper.selectById(assessmentId);
        if (a == null || studentId == null || !studentId.equals(a.getStudentId())) {
            throw new RuntimeException("无权查看该评估");
        }
        Questionnaire q = questionnaireMapper.selectById(a.getQuestionnaireId());
        MentalResponse resp = responseMapper.selectOne(
                new LambdaQueryWrapper<MentalResponse>()
                        .eq(MentalResponse::getStudentId, studentId)
                        .eq(MentalResponse::getQuestionnaireId, a.getQuestionnaireId()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assessment", a);
        out.put("questionnaire", q);
        out.put("answersJson", resp != null ? resp.getAnswers() : null);
        return out;
    }

    @Override
    public List<Map<String, Object>> listCompletion(Long questionnaireId) {
        return responseMapper.selectCompletionByQuestionnaire(questionnaireId);
    }

    /* ============== 内部辅助 ============== */

    private Long resolveStudentId(Long userId) {
        if (userId == null) return null;
        return studentLookupMapper.selectStudentIdByUserId(userId);
    }

    /** 单题计分 */
    @SuppressWarnings("unchecked")
    private int scoreForAnswer(Question q, SubmitAnswerRequest.AnswerItem ans) {
        if (TYPE_TEXT.equals(q.getQuestionType())) return 0;
        List<Integer> picked = ans.getOptionIndices();
        if (picked == null || picked.isEmpty()) return 0;

        List<Map<String, Object>> opts;
        try {
            opts = objectMapper.readValue(
                    q.getOptions() == null ? "[]" : q.getOptions(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析题目选项失败 questionId={}: {}", q.getId(), e.getMessage());
            return 0;
        }

        int s = 0;
        for (Integer idx : picked) {
            if (idx == null || idx < 0 || idx >= opts.size()) continue;
            Object scoreObj = opts.get(idx).get("score");
            if (scoreObj instanceof Number n) {
                s += n.intValue();
            }
        }
        if (TYPE_SINGLE.equals(q.getQuestionType()) && picked.size() > 1) {
            s = 0;
            Integer first = picked.get(0);
            if (first != null && first >= 0 && first < opts.size()) {
                Object scoreObj = opts.get(first).get("score");
                if (scoreObj instanceof Number n) s = n.intValue();
            }
        }
        return s;
    }

    private List<LevelRule> parseLevelRules(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析等级规则失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 选 minScore 最大且 ≤ totalScore 的等级 */
    private LevelRule pickLevelRule(List<LevelRule> rules, int totalScore) {
        if (rules == null || rules.isEmpty()) return null;
        return rules.stream()
                .filter(r -> r.getMinScore() != null && r.getMinScore() <= totalScore)
                .max(Comparator.comparingInt(LevelRule::getMinScore))
                .orElse(null);
    }
}
