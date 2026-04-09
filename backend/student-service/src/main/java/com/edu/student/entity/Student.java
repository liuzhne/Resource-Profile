package com.edu.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("student_info")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("student_id")
    private String studentId;

    private String name;

    private Integer gender;

    @TableField("birth_date")
    private LocalDate birthDate;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    @TableField("major_id")
    private Long majorId;

    @TableField("major_name")
    private String majorName;

    private String grade;

    @TableField("class_name")
    private String className;

    @TableField("enrollment_date")
    private LocalDate enrollmentDate;

    @TableField("expected_graduation")
    private LocalDate expectedGraduation;

    private BigDecimal gpa;

    private Integer credits;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
