package com.edu.auth.controller;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @GetMapping("/userInfo")
    public Result<UserInfoResponse> getUserInfo(@RequestHeader("Authorization") String token) {
        // 解析 token 获取用户ID
        String jwt = token.replace("Bearer ", "");
        Claims claims = JwtUtil.parseToken(jwt);
        Long userId = Long.valueOf(claims.get("userId").toString());

        UserInfoResponse response = authService.getUserInfo(userId);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        String jwt = token.replace("Bearer ", "");
        Claims claims = JwtUtil.parseToken(jwt);
        Long userId = Long.valueOf(claims.get("userId").toString());

        authService.logout(userId);
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }
}
