package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.student.entity.Attendance;
import com.edu.student.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** H-1.2：考勤查询端点，供 mcp-student-data 的 get_attendance tool 调用 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/{id}/attendance")
    public Result<List<Attendance>> listAttendance(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(attendanceService.listByStudentId(studentId, from, to));
    }

    @GetMapping("/{id}/attendance/summary")
    public Result<Map<String, Object>> attendanceSummary(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(attendanceService.summary(studentId, from, to));
    }
}
