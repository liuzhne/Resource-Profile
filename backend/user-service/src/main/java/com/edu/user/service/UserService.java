package com.edu.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.user.entity.User;

public interface UserService {

    Page<User> list(Integer page, Integer size, String username, Integer role, Integer status);

    User getById(Long id);

    void save(User user);

    void update(User user);

    void delete(Long id);
}
