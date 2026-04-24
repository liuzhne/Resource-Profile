package com.edu.data.controller;

import com.edu.common.result.Result;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(dashboardService.getStatistics());
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> trend() {
        return Result.success(dashboardService.getTrend());
    }

    @GetMapping("/distribution")
    public Result<Map<String, Object>> distribution() {
        return Result.success(dashboardService.getDistribution());
    }

    @GetMapping("/recentLogins")
    public Result<List<Map<String, Object>>> recentLogins() {
        return Result.success(dashboardService.getRecentLogins());
    }
}
