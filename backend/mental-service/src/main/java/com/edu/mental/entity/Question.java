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

    @TableField("scoring_rules")
    private String scoringRules;

    @TableField("scale_min")
    private Integer scaleMin;

    @TableField("scale_max")
    private Integer scaleMax;

    @TableField("scale_labels")
    private String scaleLabels;

    private Integer required;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
