package com.edu.auth.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.common.result.Result;

public class AuthFallbackHandler {

    public static Result<LoginResponse> loginBlockHandler(LoginRequest request, BlockException ex) {
        return Result.error(429, "登录请求过于频繁，请稍后重试");
    }

    public static Result<LoginResponse> loginFallback(LoginRequest request, Throwable ex) {
        return Result.error(500, "登录服务暂不可用，请稍后重试");
    }

    public static Result<UserInfoResponse> userInfoBlockHandler(String token, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<UserInfoResponse> userInfoFallback(String token, Throwable ex) {
        return Result.error(500, "服务降级：无法获取用户信息");
    }

    public static Result<Void> logoutBlockHandler(String token, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<Void> logoutFallback(String token, Throwable ex) {
        return Result.error(500, "服务降级：登出操作失败");
    }

    public static Result<LoginResponse> refreshTokenBlockHandler(String refreshToken, BlockException ex) {
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<LoginResponse> refreshTokenFallback(String refreshToken, Throwable ex) {
        return Result.error(500, "服务降级：刷新令牌失败");
    }
}
