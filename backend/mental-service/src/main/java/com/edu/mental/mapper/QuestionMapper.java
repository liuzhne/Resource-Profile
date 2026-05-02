package com.edu.mental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.mental.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
