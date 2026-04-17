package com.edu.mental.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.mental.entity.Questionnaire;

import java.util.Map;

public class MentalFallbackHandler {

    public static Result<Map<String, Object>> overviewBlockHandler(BlockException ex) {
        return Result.error(429, "心理健康概览查询过于频繁，请稍后重试");
    }

    public static Result<Map<String, Object>> overviewFallback(Throwable ex) {
        return Result.error(500, "心理健康概览服务暂不可用，请稍后重试");
    }

    public static Result<Page<Questionnaire>> listQuestionnairesBlockHandler(Integer page, Integer size, BlockException ex) {
        return Result.error(429, "问卷列表查询过于频繁，请稍后重试");
    }

    public static Result<Page<Questionnaire>> listQuestionnairesFallback(Integer page, Integer size, Throwable ex) {
        return Result.error(500, "问卷列表服务暂不可用，请稍后重试");
    }

    public static Result<Map<String, Object>> analysisBlockHandler(BlockException ex) {
        return Result.error(429, "心理分析查询过于频繁，请稍后重试");
    }

    public static Result<Map<String, Object>> analysisFallback(Throwable ex) {
        return Result.error(500, "心理分析服务暂不可用，请稍后重试");
    }
}
