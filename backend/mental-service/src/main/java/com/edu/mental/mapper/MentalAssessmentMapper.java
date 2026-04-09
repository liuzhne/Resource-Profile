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
}
