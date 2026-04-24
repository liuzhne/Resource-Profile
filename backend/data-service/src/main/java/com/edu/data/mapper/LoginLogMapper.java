package com.edu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.data.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
