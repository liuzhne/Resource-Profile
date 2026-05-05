package com.edu.mental.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mental_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("questionnaire_id")
    private Long questionnaireId;

    @TableField("sort_order")
    private Integer sortOrder;

    private String content;

    @TableField("question_type")
    private String questionType;

    private String options;

    private Integer required;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
