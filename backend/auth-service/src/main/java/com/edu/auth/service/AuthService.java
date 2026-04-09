package com.edu.auth.service;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    UserInfoResponse getUserInfo(Long userId);

    void logout(Long userId);

    LoginResponse refreshToken(String refreshToken);
}
