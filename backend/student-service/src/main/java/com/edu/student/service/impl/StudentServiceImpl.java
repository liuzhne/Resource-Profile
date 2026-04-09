package com.edu.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.student.entity.Student;
import com.edu.student.mapper.StudentMapper;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;

    @Override
    public Page<Student> list(Integer page, Integer size, String name, String dept, String grade) {
        Page<Student> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(name)) {
            wrapper.like(Student::getName, name);
        }
        if (StringUtils.hasText(dept)) {
            wrapper.eq(Student::getDeptName, dept);
        }
        if (StringUtils.hasText(grade)) {
            wrapper.eq(Student::getGrade, grade);
        }

        wrapper.orderByDesc(Student::getCreateTime);
        return studentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Student getById(Long id) {
        return studentMapper.selectById(id);
    }

    @Override
    public void save(Student student) {
        studentMapper.insert(student);
    }

    @Override
    public void update(Student student) {
        studentMapper.updateById(student);
    }

    @Override
    public void delete(Long id) {
        studentMapper.deleteById(id);
    }
}
