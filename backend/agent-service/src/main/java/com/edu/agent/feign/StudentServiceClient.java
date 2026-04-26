package com.edu.agent.feign;

import com.edu.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 调用 student-service (Port: 8084)
 * 文档端点: GET /student/{id}
 * Gateway 路由: /student/** → student-service (StripPrefix=0)
 */
@FeignClient(name = "student-service")
public interface StudentServiceClient {

    /**
     * 获取学生基础档案（姓名、年级、专业、院系等）
     */
    @GetMapping("/student/{id}")
    Result<Map<String, Object>> getStudentById(@PathVariable("id") Long  id);
}