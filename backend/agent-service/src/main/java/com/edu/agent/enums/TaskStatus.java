package com.edu.agent.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {
    PENDING("待处理"),
    RISK_ANALYZING("风险识别中"),
    KNOWLEDGE_RETRIEVING("知识检索中"),
    PLAN_GENERATING("方案生成中"),
    COMPLIANCE_CHECKING("合规审核中"),
    COMPLETED("已完成"),
    REJECTED("合规未通过，转人工"),
    FAILED("系统异常");

    private final String desc;

    TaskStatus(String desc) {
        this.desc = desc;
    }
}