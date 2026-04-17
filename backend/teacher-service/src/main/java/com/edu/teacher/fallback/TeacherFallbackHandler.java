package com.edu.teacher.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.teacher.entity.Teacher;

public class TeacherFallbackHandler {

    public static Result<Page<Teacher>> listBlockHandler(Integer page, Integer size, String name, String dept, String title, BlockException ex) {
        return Result.error(429, "教师列表查询过于频繁，请稍后重试");
    }

    public static Result<Page<Teacher>> listFallback(Integer page, Integer size, String name, String dept, String title, Throwable ex) {
        return Result.error(500, "教师列表服务暂不可用，请稍后重试");
    }

    public static Result<Teacher> getByIdBlockHandler(Long id, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<Teacher> getByIdFallback(Long id, Throwable ex) {
        return Result.error(500, "教师详情服务暂不可用，请稍后重试");
    }
}
