package com.edu.data.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.edu.common.result.Result;

import java.util.List;
import java.util.Map;

public class DashboardFallbackHandler {

    public static Result<Map<String, Object>> statisticsBlockHandler(BlockException ex) {
        return Result.error(429, "报表查询过于频繁，请稍后重试");
    }

    public static Result<Map<String, Object>> statisticsFallback(Throwable ex) {
        return Result.error(500, "报表服务暂不可用，请稍后重试");
    }

    public static Result<Map<String, Object>> trendBlockHandler(BlockException ex) {
        return Result.error(429, "趋势查询过于频繁，请稍后重试");
    }

    public static Result<Map<String, Object>> trendFallback(Throwable ex) {
        return Result.error(500, "趋势服务暂不可用，请稍后重试");
    }

    public static Result<Map<String, Object>> distributionBlockHandler(BlockException ex) {
        return Result.error(429, "分布查询过于频繁，请稍后重试");
    }

    public static Result<Map<String, Object>> distributionFallback(Throwable ex) {
        return Result.error(500, "分布服务暂不可用，请稍后重试");
    }

    public static Result<List<Map<String, Object>>> recentLoginsBlockHandler(BlockException ex) {
        return Result.error(429, "登录记录查询过于频繁，请稍后重试");
    }

    public static Result<List<Map<String, Object>>> recentLoginsFallback(Throwable ex) {
        return Result.error(500, "登录记录服务暂不可用，请稍后重试");
    }
}
