package com.edu.agent.security;

public class AgentContextHolder {
    private static final ThreadLocal<AgentSecurityContext> CONTEXT = new ThreadLocal<>();

    public static void set(AgentSecurityContext ctx) {
        CONTEXT.set(ctx);
    }

    public static AgentSecurityContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean hasRole(String role) {
        AgentSecurityContext ctx = get();
        return ctx != null && role.equals(ctx.getRole());
    }
}