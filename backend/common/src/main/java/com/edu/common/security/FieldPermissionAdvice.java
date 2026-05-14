package com.edu.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * G-2.2-a：字段级权限切面。
 *
 * <p>对所有 {@code @RestController} 返回的响应体，递归遍历对象图，遇到带
 * {@link SensitiveField} 注解的字段时，按 {@link RequestContext#getRoles()} 与
 * docs/educare/FIELD_PERMISSION.md §4 的矩阵决定是否置 {@code null}。
 *
 * <p><b>启用方式</b>：在 service 的 application.yml 加
 * {@code educare.field-permission.enabled: true}（默认 false，避免依赖链未补齐
 * 时误伤）。
 *
 * <p><b>不支持的场景</b>（显式落档）：
 * <ul>
 *   <li>行级权限（teacher 只看本班学生）—— 留 Phase I-4 合规框架。</li>
 *   <li>资源所有人（"本人"）判定 —— 一阶段不实现。</li>
 *   <li>{@code @ResponseBody} 之外的渲染路径（如直接 write 到 HttpServletResponse 的）。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnProperty(value = "educare.field-permission.enabled", havingValue = "true")
public class FieldPermissionAdvice implements ResponseBodyAdvice<Object> {

    /** 不进入对象内部递归的"叶子"包前缀，避免反射 JDK / Spring 内部类。 */
    private static final List<String> SKIP_PACKAGE_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "sun.", "com.sun.",
            "org.springframework.", "com.fasterxml.", "org.slf4j.",
            "com.baomidou.mybatisplus.core.toolkit." // MP 内部 helper
    );

    /** Field 反射结果缓存。null 值表示该类没有任何带 @SensitiveField 的字段（仍需递归子对象）。 */
    private static final Map<Class<?>, FieldDescriptor[]> CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) return null;
        Set<String> roles = RequestContext.getRoles();
        // 没拿到角色：保守起见走最低权限（只 PUBLIC），但不抛错以免破坏链路
        if (roles.isEmpty()) {
            log.debug("FieldPermissionAdvice: empty roles, applying PUBLIC-only filter on {}", body.getClass().getName());
        }
        try {
            walk(body, roles, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (Exception e) {
            log.warn("FieldPermissionAdvice 处理失败（已透传原 body）: {}", e.getMessage());
        }
        return body;
    }

    // ---------------- 内部 ----------------

    private void walk(Object obj, Set<String> roles, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) return;
        Class<?> cls = obj.getClass();
        if (isLeaf(cls)) return;
        visited.add(obj);

        if (obj instanceof Collection<?> coll) {
            for (Object el : coll) walk(el, roles, visited);
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) walk(v, roles, visited);
            return;
        }
        if (cls.isArray()) {
            int n = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < n; i++) walk(java.lang.reflect.Array.get(obj, i), roles, visited);
            return;
        }

        FieldDescriptor[] fields = CACHE.computeIfAbsent(cls, FieldPermissionAdvice::scan);
        for (FieldDescriptor fd : fields) {
            try {
                Object value = fd.field.get(obj);
                if (fd.sensitivity != null && !canSee(roles, fd.sensitivity)) {
                    fd.field.set(obj, null);
                    continue;
                }
                walk(value, roles, visited);
            } catch (IllegalAccessException ignored) {
                // setAccessible(true) 已开，不应到这里
            }
        }
    }

    private static FieldDescriptor[] scan(Class<?> cls) {
        // 走全继承链
        List<FieldDescriptor> out = new java.util.ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                f.setAccessible(true);
                SensitiveField ann = f.getAnnotation(SensitiveField.class);
                Sensitivity s = ann == null ? null : ann.value();
                out.add(new FieldDescriptor(f, s));
            }
        }
        return out.toArray(new FieldDescriptor[0]);
    }

    private static boolean isLeaf(Class<?> cls) {
        if (cls.isPrimitive() || cls.isEnum()) return true;
        String name = cls.getName();
        return SKIP_PACKAGE_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /**
     * 角色 × 分级 决策（docs/educare/FIELD_PERMISSION.md §4 的列级部分）。
     * teacher / student 在本期暂归入与 academic_advisor 相近的列级判定；
     * 行级（teacher 只看本班、student 本人）留 Phase I-4。
     */
    static boolean canSee(Set<String> roles, Sensitivity tier) {
        if (roles.contains("admin")) return true;
        return switch (tier) {
            case PUBLIC -> true;
            case MEDIUM -> Stream.of("psychologist", "counselor", "academic_advisor", "teacher")
                                  .anyMatch(roles::contains);
            case HIGH -> Stream.of("psychologist", "counselor").anyMatch(roles::contains);
            case EXTREME -> roles.contains("psychologist");
        };
    }

    private record FieldDescriptor(Field field, Sensitivity sensitivity) {}
}
