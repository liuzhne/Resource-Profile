package com.edu.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H-3.5：Skill 加载器。
 *
 * <p>按需把 {@code skills/&lt;name&gt;.md} 解析为 {@link SkillDefinition} 并注入 AgentLoop 的 system prompt。
 * 支持两种来源（优先级从高到低）：
 * <ol>
 *   <li><b>外部文件系统目录</b>（{@code educare.agent.skills.dir} 配置且存在）—— 支持<b>热更新</b>：
 *       每次读取比对文件 mtime，变化即重载，编辑 md 不需重启。生产可挂载该目录覆盖内置技能。</li>
 *   <li><b>classpath {@code skills/}</b>（内置兜底）—— 打包进 jar，加载一次后缓存（不热更新）。</li>
 * </ol>
 *
 * <p>"按需"通过 {@code educare.agent.skills.active}（逗号分隔、有序）选择当前任务注入哪些技能，
 * 避免把全部技能无脑塞进 prompt。
 */
@Slf4j
@Component
public class SkillLoader {

    @Value("${educare.agent.skills.enabled:true}")
    private boolean enabled;

    /** 外部技能目录；空表示只用 classpath 内置。 */
    @Value("${educare.agent.skills.dir:}")
    private String skillsDir;

    /** 当前激活、按序注入的技能名。 */
    @Value("${educare.agent.skills.active:risk-assessment,psychological-screening,intervention-design,compliance-audit}")
    private String activeCsv;

    /** name → (mtime, def)；mtime=-1 表示来自 classpath（不热更新）。 */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(long mtime, SkillDefinition def) { }

    public boolean isEnabled() {
        return enabled;
    }

    /** 配置中激活的技能名（有序、去空白）。 */
    public List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (String n : activeCsv.split(",")) {
            String t = n.strip();
            if (!t.isEmpty()) {
                names.add(t);
            }
        }
        return names;
    }

    /** 读取单个技能；不存在或解析失败返回 empty。 */
    public Optional<SkillDefinition> getSkill(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.strip();
        try {
            Path fsPath = resolveFsPath(key);
            if (fsPath != null) {
                long mtime = Files.getLastModifiedTime(fsPath).toMillis();
                Cached c = cache.get(key);
                if (c != null && c.mtime == mtime) {
                    return Optional.of(c.def);
                }
                String raw = Files.readString(fsPath, StandardCharsets.UTF_8);
                SkillDefinition def = parse(key, raw);
                cache.put(key, new Cached(mtime, def));
                log.debug("H-3：从外部目录加载技能 {}（mtime={}）", key, mtime);
                return Optional.of(def);
            }
            // classpath 兜底，加载一次缓存
            Cached c = cache.get(key);
            if (c != null && c.mtime == -1L) {
                return Optional.of(c.def);
            }
            ClassPathResource res = new ClassPathResource("skills/" + key + ".md");
            if (!res.exists()) {
                log.warn("H-3：技能 {} 不存在（classpath skills/{}.md 缺失）", key, key);
                return Optional.empty();
            }
            try (InputStream in = res.getInputStream()) {
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                SkillDefinition def = parse(key, raw);
                cache.put(key, new Cached(-1L, def));
                return Optional.of(def);
            }
        } catch (IOException e) {
            log.warn("H-3：读取技能 {} 失败: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 当前激活的技能定义（按配置顺序，跳过缺失的）。 */
    public List<SkillDefinition> listActive() {
        List<SkillDefinition> out = new ArrayList<>();
        for (String name : activeNames()) {
            getSkill(name).ifPresent(out::add);
        }
        return out;
    }

    /**
     * 把激活技能拼成可注入 system prompt 的一段 markdown。
     * 关闭或无激活技能时返回空串（调用方按"无技能"处理，保持原 prompt 字节稳定）。
     */
    public String composeActiveSkillsPrompt() {
        if (!enabled) {
            return "";
        }
        List<SkillDefinition> skills = listActive();
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("# 技能手册（按需运用）\n");
        sb.append("以下是本次任务可运用的专业技能，请在相应阶段遵循其方法与红线：\n\n");
        for (SkillDefinition s : skills) {
            sb.append(s.toPromptBlock()).append('\n');
        }
        return sb.toString();
    }

    /** 若配置了外部目录且对应文件存在，返回其 Path；否则 null（走 classpath）。 */
    private Path resolveFsPath(String name) {
        if (skillsDir == null || skillsDir.isBlank()) {
            return null;
        }
        Path p = Path.of(skillsDir, name + ".md");
        return Files.isReadable(p) ? p : null;
    }

    /** 解析 frontmatter（--- 包裹的 key: value）+ 正文。frontmatter 缺失时整体作正文。 */
    static SkillDefinition parse(String name, String raw) {
        String content = raw == null ? "" : raw.replace("\r\n", "\n");
        String description = "";
        String whenToUse = "";
        String body = content;

        String trimmed = content.stripLeading();
        if (trimmed.startsWith("---")) {
            int firstNl = trimmed.indexOf('\n');
            int end = trimmed.indexOf("\n---", firstNl);
            if (firstNl > 0 && end > firstNl) {
                String front = trimmed.substring(firstNl + 1, end);
                Map<String, String> fm = parseFrontmatter(front);
                description = fm.getOrDefault("description", "");
                whenToUse = fm.getOrDefault("when_to_use", "");
                int bodyStart = trimmed.indexOf('\n', end + 1);
                body = bodyStart >= 0 ? trimmed.substring(bodyStart + 1) : "";
            }
        }
        return new SkillDefinition(name, description, whenToUse, body.strip());
    }

    private static Map<String, String> parseFrontmatter(String front) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : Arrays.asList(front.split("\n"))) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String k = line.substring(0, colon).strip();
                String v = line.substring(colon + 1).strip();
                if (!k.isEmpty()) {
                    map.put(k, v);
                }
            }
        }
        return map;
    }
}
