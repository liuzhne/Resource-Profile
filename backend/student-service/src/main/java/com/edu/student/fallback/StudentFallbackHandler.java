package com.edu.student.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.student.entity.Student;

public class StudentFallbackHandler {

    public static Result<Page<Student>> listBlockHandler(Integer page, Integer size, String name, String dept, String grade, BlockException ex) {
        return Result.error(429, "学生列表查询过于频繁，请稍后重试");
    }

    public static Result<Page<Student>> listFallback(Integer page, Integer size, String name, String dept, String grade, Throwable ex) {
        return Result.error(500, "学生列表服务暂不可用，请稍后重试");
    }

    public static Result<Student> getByIdBlockHandler(Long id, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<Student> getByIdFallback(Long id, Throwable ex) {
        return Result.error(500, "学生详情服务暂不可用，请稍后重试");
    }
}
