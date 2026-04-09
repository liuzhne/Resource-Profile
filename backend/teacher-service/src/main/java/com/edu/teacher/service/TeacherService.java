package com.edu.teacher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.teacher.entity.Teacher;

public interface TeacherService {

    Page<Teacher> list(Integer page, Integer size, String name, String dept, String title);

    Teacher getById(Long id);

    void save(Teacher teacher);

    void update(Teacher teacher);

    void delete(Long id);
}
