package com.edu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.user.entity.User;
import com.edu.user.mapper.UserMapper;
import com.edu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Page<User> list(Integer page, Integer size, String username, Integer role, Integer status) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (role != null) {
            wrapper.eq(User::getUserType, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }

        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public void save(User user) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("密码不能为空");
        }
        user.setPassword(encodePassword(user.getPassword()));
        userMapper.insert(user);
    }

    @Override
    public void update(User user) {
        if (user.getPassword() != null) {
            if (user.getPassword().isBlank()) {
                user.setPassword(null);
            } else {
                user.setPassword(encodePassword(user.getPassword()));
            }
        }
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    private String encodePassword(String rawPassword) {
        if (rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$") || rawPassword.startsWith("$2y$")) {
            return rawPassword;
        }
        return passwordEncoder.encode(rawPassword);
    }
}
