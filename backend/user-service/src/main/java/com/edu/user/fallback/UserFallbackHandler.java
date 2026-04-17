package com.edu.user.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.user.entity.User;

public class UserFallbackHandler {

    public static Result<Page<User>> listBlockHandler(Integer page, Integer size, String username, Integer role, Integer status, BlockException ex) {
        return Result.error(429, "用户列表查询过于频繁，请稍后重试");
    }

    public static Result<Page<User>> listFallback(Integer page, Integer size, String username, Integer role, Integer status, Throwable ex) {
        return Result.error(500, "用户列表服务暂不可用，请稍后重试");
    }

    public static Result<User> getByIdBlockHandler(Long id, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<User> getByIdFallback(Long id, Throwable ex) {
        return Result.error(500, "用户详情服务暂不可用，请稍后重试");
    }
}
