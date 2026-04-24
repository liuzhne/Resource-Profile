package com.edu.data.service;

import java.util.List;
import java.util.Map;

public interface DashboardService {

    Map<String, Object> getStatistics();

    Map<String, Object> getTrend();

    Map<String, Object> getDistribution();

    List<Map<String, Object>> getRecentLogins();
}
