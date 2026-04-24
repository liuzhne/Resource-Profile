package com.edu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.data.entity.StudentInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentInfoMapper extends BaseMapper<StudentInfo> {
}
