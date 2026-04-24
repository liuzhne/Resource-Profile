package com.edu.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mental_assessment")
public class MentalAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("questionnaire_id")
    private Long questionnaireId;

    private Integer score;

    private String level;

    private String result;

    private String suggestion;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
