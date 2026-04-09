package com.edu.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 密钥，应从环境变量或配置中心读取
     */
    private String secret;

    /**
     * 访问令牌过期时间（毫秒），默认 24 小时
     */
    private long accessTokenExpire = 24 * 60 * 60 * 1000;

    /**
     * 刷新令牌过期时间（毫秒），默认 7 天
     */
    private long refreshTokenExpire = 7 * 24 * 60 * 60 * 1000;
}
