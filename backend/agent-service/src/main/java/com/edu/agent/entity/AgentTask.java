package com.edu.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String studentId;

    @TableField("status")
    private TaskStatus status;

    @TableField("risk_level")
    private RiskLevel riskLevel;

    private Integer riskScore;

    @TableField("risk_type")
    private String riskType;

    @TableField("risk_analysis_result")
    private String riskAnalysisResult;

    @TableField("retrieved_knowledge")
    private String retrievedKnowledge;

    @TableField("intervention_plan")
    private String interventionPlan;

    @TableField("compliance_audit")
    private String complianceAudit;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime completedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}