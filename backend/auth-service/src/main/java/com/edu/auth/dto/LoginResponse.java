package com.edu.auth.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String refreshToken;
    private Long expiresIn;
}
