package com.edu.student.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.student.entity.Student;
import com.edu.student.fallback.StudentFallbackHandler;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping("/list")
    @SentinelResource(
            value = "student_list",
            blockHandlerClass = StudentFallbackHandler.class,
            blockHandler = "listBlockHandler",
            fallbackClass = StudentFallbackHandler.class,
            fallback = "listFallback"
    )
    public Result<Page<Student>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String grade) {
        Page<Student> result = studentService.list(page, size, name, dept, grade);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @SentinelResource(
            value = "student_getById",
            blockHandlerClass = StudentFallbackHandler.class,
            blockHandler = "getByIdBlockHandler",
            fallbackClass = StudentFallbackHandler.class,
            fallback = "getByIdFallback"
    )
    public Result<Student> getById(@PathVariable Long id) {
        Student student = studentService.getById(id);
        return Result.success(student);
    }

    @PostMapping
    public Result<Void> save(@RequestBody Student student) {
        studentService.save(student);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Student student) {
        student.setId(id);
        studentService.update(student);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.success();
    }
}
