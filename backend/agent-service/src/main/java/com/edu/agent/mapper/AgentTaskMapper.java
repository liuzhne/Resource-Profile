package com.edu.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * 乐观锁状态流转：只有当前状态=oldStatus时才更新
     */
    @Update("UPDATE agent_task SET status = #{newStatus}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{oldStatus} AND deleted = 0")
    int updateStatus(@Param("id") Long id,
                     @Param("oldStatus") String oldStatus,
                     @Param("newStatus") String newStatus);
}