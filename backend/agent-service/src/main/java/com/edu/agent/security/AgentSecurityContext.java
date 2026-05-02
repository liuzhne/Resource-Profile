package com.edu.agent.security;

import lombok.Data;

@Data
public class AgentSecurityContext {
    /**
     * 当前操作者角色：counselor / teacher / psychologist / academic_advisor / admin
     */
    private String role;

    /**
     * 是否允许查看原始敏感数据
     */
    private boolean sensitiveDataAllowed;

    public static final String ROLE_PSYCHOLOGIST = "psychologist";
    public static final String ROLE_COUNSELOR = "counselor";
    public static final String ROLE_ACADEMIC_ADVISOR = "academic_advisor";
    public static final String ROLE_ADMIN = "admin";
}