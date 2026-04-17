package com.edu.data.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.edu.common.result.Result;
import com.edu.data.fallback.DashboardFallbackHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    @GetMapping("/statistics")
    @SentinelResource(
            value = "data_dashboard_statistics",
            blockHandlerClass = DashboardFallbackHandler.class,
            blockHandler = "statisticsBlockHandler",
            fallbackClass = DashboardFallbackHandler.class,
            fallback = "statisticsFallback"
    )
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> result = new HashMap<>();

        result.put("teacherCount", 128);
        result.put("studentCount", 2456);
        result.put("questionnaireCount", 89);
        result.put("warningCount", 5);

        return Result.success(result);
    }

    @GetMapping("/trend")
    @SentinelResource(
            value = "data_dashboard_trend",
            blockHandlerClass = DashboardFallbackHandler.class,
            blockHandler = "trendBlockHandler",
            fallbackClass = DashboardFallbackHandler.class,
            fallback = "trendFallback"
    )
    public Result<Map<String, Object>> trend() {
        Map<String, Object> result = new HashMap<>();

        result.put("days", List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日"));
        result.put("teacherData", List.of(120, 132, 101, 134, 90, 230, 210));
        result.put("studentData", List.of(220, 182, 191, 234, 290, 330, 310));

        return Result.success(result);
    }

    @GetMapping("/distribution")
    @SentinelResource(
            value = "data_dashboard_distribution",
            blockHandlerClass = DashboardFallbackHandler.class,
            blockHandler = "distributionBlockHandler",
            fallbackClass = DashboardFallbackHandler.class,
            fallback = "distributionFallback"
    )
    public Result<Map<String, Object>> distribution() {
        Map<String, Object> result = new HashMap<>();

        result.put("data", List.of(
                Map.of("name", "教师", "value", 128),
                Map.of("name", "学生", "value", 2456),
                Map.of("name", "行政人员", "value", 45)
        ));

        return Result.success(result);
    }

    @GetMapping("/recentLogins")
    @SentinelResource(
            value = "data_dashboard_recentLogins",
            blockHandlerClass = DashboardFallbackHandler.class,
            blockHandler = "recentLoginsBlockHandler",
            fallbackClass = DashboardFallbackHandler.class,
            fallback = "recentLoginsFallback"
    )
    public Result<List<Map<String, Object>>> recentLogins() {
        return Result.success(List.of(
                Map.of("username", "张老师", "role", "教师", "time", "2026-04-05 14:30", "ip", "192.168.1.100"),
                Map.of("username", "李同学", "role", "学生", "time", "2026-04-05 13:45", "ip", "192.168.1.101"),
                Map.of("username", "王管理员", "role", "管理员", "time", "2026-04-05 12:00", "ip", "192.168.1.102")
        ));
    }
}
