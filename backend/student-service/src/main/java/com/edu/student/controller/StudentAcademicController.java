package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.student.entity.AcademicRecord;
import com.edu.student.service.AcademicRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** H-1.2：成绩查询端点，供 mcp-student-data 的 get_academic_history tool 调用 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentAcademicController {

    private final AcademicRecordService academicRecordService;

    @GetMapping("/{id}/academic")
    public Result<List<AcademicRecord>> listAcademic(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) String term) {
        return Result.success(academicRecordService.listByStudentId(studentId, term));
    }
}
