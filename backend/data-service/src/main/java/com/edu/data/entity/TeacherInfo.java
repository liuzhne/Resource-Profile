package com.edu.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("teacher_info")
public class TeacherInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("employee_id")
    private String employeeId;

    private String name;

    private Integer gender;

    @TableField("birth_date")
    private LocalDate birthDate;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    private String title;

    private String education;

    private String school;

    private String major;

    @TableField("research_area")
    private String researchArea;

    @TableField("join_date")
    private LocalDate joinDate;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
