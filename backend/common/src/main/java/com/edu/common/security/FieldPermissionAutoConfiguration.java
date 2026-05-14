package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * G-2.2-e：字段权限切面 + 角色上下文过滤器的自动装配。
 *
 * <p>由 {@code educare.field-permission.enabled=true} 启用（默认 false，避免还没
 * 用上的 service 在升级 common 包后被默认拦截而误伤）。
 *
 * <p>装配两个 bean：
 * <ul>
 *   <li>{@link FieldPermissionAdvice} —— `@RestControllerAdvice`，对所有
 *       `@RestController` 响应做字段过滤；类自身有 `@ConditionalOnProperty`，
 *       这里再加 `@ConditionalOnMissingBean` 防止与组件扫描重复。</li>
 *   <li>{@link RoleContextFilter} —— `OncePerRequestFilter`，把 JWT roles claim
 *       灌进 {@link RequestContext}；通过 {@link FilterRegistrationBean} 显式注册
 *       URL 与执行顺序（靠近 dispatcher 末尾，保证 auth 类 filter 先于本 filter 跑完）。</li>
 * </ul>
 *
 * <p><b>注</b>：依赖 {@link JwtUtil} bean（由 common 的另一条 autoconfig 入口注册）。
 */
@AutoConfiguration
@ConditionalOnProperty(value = "educare.field-permission.enabled", havingValue = "true")
public class FieldPermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FieldPermissionAdvice fieldPermissionAdvice() {
        return new FieldPermissionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleContextFilter roleContextFilter(JwtUtil jwtUtil) {
        return new RoleContextFilter(jwtUtil);
    }

    @Bean
    public FilterRegistrationBean<RoleContextFilter> roleContextFilterRegistration(RoleContextFilter filter) {
        FilterRegistrationBean<RoleContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        // 靠近 dispatcher 末尾：auth/security filter（高优先级）先验证 token，
        // 我们再读 token 灌 roles。失败也不抛错，保守降级。
        reg.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return reg;
    }
}
