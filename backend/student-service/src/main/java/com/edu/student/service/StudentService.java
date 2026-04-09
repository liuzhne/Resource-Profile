package com.edu.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.student.entity.Student;

public interface StudentService {

    Page<Student> list(Integer page, Integer size, String name, String dept, String grade);

    Student getById(Long id);

    void save(Student student);

    void update(Student student);

    void delete(Long id);
}
