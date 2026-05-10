package com.edu.agent.controller;

import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.service.ExportService;
import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * F-1：PDF 报告异步导出。
 * 三段式：触发 / 状态 / 下载。所有端点走 /agent/api/v1 与现有 task controller 同命名空间。
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final JwtUtil jwtUtil;

    @PostMapping("/task/{taskId}/export")
    public Result<Map<String, Long>> createExportJob(
            @PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = extractUserId(authHeader);
        try {
            Long jobId = exportService.createExportJob(taskId, userId);
            return Result.success(Map.of("jobId", jobId));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("F-1：创建导出任务失败 taskId={}", taskId, e);
            return Result.error("创建导出任务失败：" + e.getMessage());
        }
    }

    @GetMapping("/export/{jobId}")
    public Result<AgentExportTask> getExportStatus(@PathVariable Long jobId) {
        AgentExportTask job = exportService.getJobStatus(jobId);
        if (job == null) {
            return Result.error(404, "导出任务不存在");
        }
        return Result.success(job);
    }

    @GetMapping("/export/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long jobId) {
        Resource resource;
        AgentExportTask job;
        try {
            job = exportService.getJobStatus(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            resource = exportService.loadFile(jobId);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }

        String filename = String.format("report-%d.pdf", job.getTaskId());
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    /**
     * 解析 Authorization 头取 subject 作为 userId。
     * 失败时返回 0L —— 与现有 AgentTaskController 一致，未鉴权调用也允许（项目里 gateway 已做鉴权层），
     * 这里只做审计字段填充。
     */
    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return 0L;
        try {
            String token = authHeader.substring(7);
            String subject = jwtUtil.getSubject(token);
            return subject == null ? 0L : Long.parseLong(subject);
        } catch (Exception e) {
            log.debug("解析 Authorization 失败：{}", e.getMessage());
            return 0L;
        }
    }
}
