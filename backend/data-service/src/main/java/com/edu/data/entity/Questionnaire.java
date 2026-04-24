package com.edu.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mental_questionnaire")
public class Questionnaire {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String type;

    private String description;

    private Integer questions;

    @TableField("start_time")
    private LocalDate startTime;

    @TableField("end_time")
    private LocalDate endTime;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
