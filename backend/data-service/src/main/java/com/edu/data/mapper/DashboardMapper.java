package com.edu.data.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    Long countActiveTeachers();

    Long countActiveStudents();

    Long countActiveQuestionnaires();

    Long countWarningStudents();

    List<Map<String, Object>> selectTeacherTrend(@Param("startDate") LocalDate startDate);

    List<Map<String, Object>> selectStudentTrend(@Param("startDate") LocalDate startDate);

    List<Map<String, Object>> selectUserDistribution();

    List<Map<String, Object>> selectRecentLogins();
}
