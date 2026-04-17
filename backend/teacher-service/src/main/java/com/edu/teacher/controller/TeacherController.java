package com.edu.teacher.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.fallback.TeacherFallbackHandler;
import com.edu.teacher.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping("/list")
    @SentinelResource(
            value = "teacher_list",
            blockHandlerClass = TeacherFallbackHandler.class,
            blockHandler = "listBlockHandler",
            fallbackClass = TeacherFallbackHandler.class,
            fallback = "listFallback"
    )
    public Result<Page<Teacher>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String title) {
        Page<Teacher> result = teacherService.list(page, size, name, dept, title);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @SentinelResource(
            value = "teacher_getById",
            blockHandlerClass = TeacherFallbackHandler.class,
            blockHandler = "getByIdBlockHandler",
            fallbackClass = TeacherFallbackHandler.class,
            fallback = "getByIdFallback"
    )
    public Result<Teacher> getById(@PathVariable Long id) {
        Teacher teacher = teacherService.getById(id);
        return Result.success(teacher);
    }

    @PostMapping
    public Result<Void> save(@RequestBody Teacher teacher) {
        teacherService.save(teacher);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Teacher teacher) {
        teacher.setId(id);
        teacherService.update(teacher);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return Result.success();
    }
}
