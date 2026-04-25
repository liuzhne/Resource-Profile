package com.edu.mental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.mental.entity.MentalAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MentalAssessmentMapper extends BaseMapper<MentalAssessment> {

    @Select("SELECT level, COUNT(*) as count FROM mental_assessment " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY level")
    List<Map<String, Object>> selectRecentStats();

    @Select("SELECT si.name, si.dept_name as dept, ma.level, " +
            "DATE_FORMAT(ma.create_time, '%Y-%m-%d') as time " +
            "FROM mental_assessment ma " +
            "JOIN student_info si ON ma.student_id = si.id " +
            "WHERE ma.level IN ('高危', '重度', '中度') AND ma.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "ORDER BY ma.create_time DESC LIMIT 10")
    List<Map<String, Object>> selectWarningList();

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') as month, level, COUNT(*) as count " +
            "FROM mental_assessment " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m'), level " +
            "ORDER BY month")
    List<Map<String, Object>> selectTrendData();

    @Select("SELECT si.dept_name as dept, " +
            "SUM(CASE WHEN ma.level IN ('正常','轻度') THEN 1 ELSE 0 END) as good, " +
            "SUM(CASE WHEN ma.level = '中度' THEN 1 ELSE 0 END) as attention, " +
            "SUM(CASE WHEN ma.level IN ('重度','高危') THEN 1 ELSE 0 END) as intervention " +
            "FROM mental_assessment ma " +
            "JOIN student_info si ON ma.student_id = si.id " +
            "WHERE ma.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY si.dept_name")
    List<Map<String, Object>> selectDeptDistribution();

    @Select("SELECT si.grade, " +
            "ROUND(SUM(CASE WHEN ma.level IN ('正常','轻度') THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 0) as rate " +
            "FROM mental_assessment ma " +
            "JOIN student_info si ON ma.student_id = si.id " +
            "WHERE ma.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY si.grade ORDER BY si.grade")
    List<Map<String, Object>> selectGradeComparison();

    @Select("SELECT ma.level as `group`, COUNT(DISTINCT ma.student_id) as `count`, " +
            "CASE WHEN ma.level IN ('重度','高危') THEN '高' WHEN ma.level = '中度' THEN '中' ELSE '低' END as risk " +
            "FROM mental_assessment ma " +
            "WHERE ma.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND ma.level NOT IN ('正常','轻度') " +
            "GROUP BY ma.level")
    List<Map<String, Object>> selectFocusGroups();

    @Select("SELECT si.gender, " +
            "SUM(CASE WHEN ma.level IN ('正常','轻度') THEN 1 ELSE 0 END) as good, " +
            "SUM(CASE WHEN ma.level = '中度' THEN 1 ELSE 0 END) as attention, " +
            "SUM(CASE WHEN ma.level IN ('重度','高危') THEN 1 ELSE 0 END) as intervention " +
            "FROM mental_assessment ma " +
            "JOIN student_info si ON ma.student_id = si.id " +
            "WHERE ma.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY si.gender")
    List<Map<String, Object>> selectGenderAnalysis();
}
