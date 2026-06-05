package com.edu.common.util;

import com.edu.common.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        // 从配置中读取密钥
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT secret must be configured. Please set jwt.secret in configuration.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, jwtProperties.getAccessTokenExpire());
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String subject) {
        return generateToken(subject, null, jwtProperties.getRefreshTokenExpire());
    }

    /**
     * 生成令牌
     */
    private String generateToken(String subject, Map<String, Object> claims, long expire) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);

        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
         * Parse and verify the given JWT and return its claims.
         *
         * @param token the compact JWT string to parse and verify
         * @return the parsed JWT {@code Claims}
         * @throws io.jsonwebtoken.ExpiredJwtException    if the token is expired
         * @throws io.jsonwebtoken.UnsupportedJwtException if the token has an unsupported format
         * @throws io.jsonwebtoken.MalformedJwtException  if the token is malformed
         * @throws Exception                              for other token parsing or verification errors
         */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 过期是预期的用户行为（24h 失效），不是系统错误。降到 debug 避免污染 ERROR 日志。
            log.debug("Token已过期: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("Token非法: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 验证令牌是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从令牌中获取用户ID
     */
    public String getSubject(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从令牌中获取声明
     */
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String token, String claim) {
        Claims claims = parseToken(token);
        return (T) claims.get(claim);
    }

    /**
     * 获取令牌过期时间
     */
    public Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 判断令牌是否即将过期（小于30分钟）
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpiration(token);
        return expiration.before(new Date(System.currentTimeMillis() + 30 * 60 * 1000));
    }
}
