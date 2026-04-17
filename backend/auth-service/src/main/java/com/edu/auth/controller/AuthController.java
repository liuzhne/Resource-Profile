package com.edu.auth.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.fallback.AuthFallbackHandler;
import com.edu.auth.service.AuthService;
import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @SentinelResource(
            value = "auth_login",
            blockHandlerClass = AuthFallbackHandler.class,
            blockHandler = "loginBlockHandler",
            fallbackClass = AuthFallbackHandler.class,
            fallback = "loginFallback"
    )
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @GetMapping("/userInfo")
    @SentinelResource(
            value = "auth_userInfo",
            blockHandlerClass = AuthFallbackHandler.class,
            blockHandler = "userInfoBlockHandler",
            fallbackClass = AuthFallbackHandler.class,
            fallback = "userInfoFallback"
    )
    public Result<UserInfoResponse> getUserInfo(@RequestHeader("Authorization") String token) {
        String jwt = token.replace("Bearer ", "");
        Claims claims = jwtUtil.parseToken(jwt);
        Long userId = Long.valueOf(claims.get("userId").toString());

        UserInfoResponse response = authService.getUserInfo(userId);
        return Result.success(response);
    }

    @PostMapping("/logout")
    @SentinelResource(
            value = "auth_logout",
            blockHandlerClass = AuthFallbackHandler.class,
            blockHandler = "logoutBlockHandler",
            fallbackClass = AuthFallbackHandler.class,
            fallback = "logoutFallback"
    )
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        String jwt = token.replace("Bearer ", "");
        Claims claims = jwtUtil.parseToken(jwt);
        Long userId = Long.valueOf(claims.get("userId").toString());

        authService.logout(userId);
        return Result.success();
    }

    @PostMapping("/refresh")
    @SentinelResource(
            value = "auth_refresh",
            blockHandlerClass = AuthFallbackHandler.class,
            blockHandler = "refreshTokenBlockHandler",
            fallbackClass = AuthFallbackHandler.class,
            fallback = "refreshTokenFallback"
    )
    public Result<LoginResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }
}
