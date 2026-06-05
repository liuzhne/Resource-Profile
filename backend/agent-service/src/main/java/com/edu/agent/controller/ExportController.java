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

    /**
     * Creates an asynchronous PDF export job for the specified task.
     *
     * @param taskId     the ID of the task to export
     * @param authHeader optional `Authorization` header (Bearer token); the method extracts the JWT subject as the user ID and uses `0` when the header is missing or invalid
     * @return a `Result` containing a map with key `"jobId"` and the created export job ID on success; returns a `Result.error` with an error message (404 when the task is not found) on failure
     */
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

    /**
     * Retrieve the current status and details of an export job.
     *
     * @param jobId the identifier of the export job
     * @return a Result containing the AgentExportTask when found, or an error Result with status 404 if the job does not exist
     */
    @GetMapping("/export/{jobId}")
    public Result<AgentExportTask> getExportStatus(@PathVariable Long jobId) {
        AgentExportTask job = exportService.getJobStatus(jobId);
        if (job == null) {
            return Result.error(404, "导出任务不存在");
        }
        return Result.success(job);
    }

    /**
     * Downloads the PDF file for the specified export job as an attachment.
     *
     * <p>Returns a 200 response with `Content-Type: application/pdf` and a
     * `Content-Disposition` header containing both a plain `filename` and a UTF-8
     * `filename*` when the file is available. Returns 404 if the export job does
     * not exist, or 400 with an `X-Error-Reason` header if the job is not in a
     * state that allows downloading.
     *
     * @param jobId the export job identifier
     * @return a ResponseEntity containing the PDF resource as an attachment, or a 404/400 response when unavailable
     */
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
     * Extracts the JWT subject from an Authorization header and returns it as the numeric user ID.
     *
     * @param authHeader the raw value of the HTTP `Authorization` header (may be null or not start with "Bearer ")
     * @return the subject parsed as a `Long` when present and numeric; `0L` when the header is missing, not a Bearer token, the subject is null, or parsing fails
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
