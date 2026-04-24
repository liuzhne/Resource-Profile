package com.edu.data.service.impl;

import com.edu.data.mapper.DashboardMapper;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("teacherCount", dashboardMapper.countActiveTeachers());
        result.put("studentCount", dashboardMapper.countActiveStudents());
        result.put("questionnaireCount", dashboardMapper.countActiveQuestionnaires());
        result.put("warningCount", dashboardMapper.countWarningStudents());
        return result;
    }

    @Override
    public Map<String, Object> getTrend() {
        LocalDate startDate = LocalDate.now().minusDays(6);

        List<Map<String, Object>> teacherTrend = dashboardMapper.selectTeacherTrend(startDate);
        List<Map<String, Object>> studentTrend = dashboardMapper.selectStudentTrend(startDate);

        Map<LocalDate, Long> teacherMap = new HashMap<>();
        for (Map<String, Object> item : teacherTrend) {
            LocalDate day = ((java.sql.Date) item.get("day")).toLocalDate();
            Long count = ((Number) item.get("count")).longValue();
            teacherMap.put(day, count);
        }

        Map<LocalDate, Long> studentMap = new HashMap<>();
        for (Map<String, Object> item : studentTrend) {
            LocalDate day = ((java.sql.Date) item.get("day")).toLocalDate();
            Long count = ((Number) item.get("count")).longValue();
            studentMap.put(day, count);
        }

        String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<String> days = new ArrayList<>();
        List<Long> teacherData = new ArrayList<>();
        List<Long> studentData = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            int dayOfWeek = date.getDayOfWeek().getValue() - 1;
            days.add(weekDays[dayOfWeek]);
            teacherData.add(teacherMap.getOrDefault(date, 0L));
            studentData.add(studentMap.getOrDefault(date, 0L));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("teacherData", teacherData);
        result.put("studentData", studentData);
        return result;
    }

    @Override
    public Map<String, Object> getDistribution() {
        List<Map<String, Object>> list = dashboardMapper.selectUserDistribution();
        Map<String, Object> result = new HashMap<>();
        result.put("data", list);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentLogins() {
        return dashboardMapper.selectRecentLogins();
    }
}
