package com.edu.mental.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentLookupMapper {

    @Select("SELECT id FROM student_info WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    Long selectStudentIdByUserId(Long userId);
}
