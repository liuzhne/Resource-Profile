package com.edu.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtUtil {

    // JWT密钥，实际项目中应从配置文件读取
    private static final String SECRET = "edu-portrait-jwt-secret-key-12345678901234567890";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // 访问令牌过期时间：24小时
    private static final long ACCESS_TOKEN_EXPIRE = 24 * 60 * 60 * 1000;

    // 刷新令牌过期时间：7天
    private static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 60 * 60 * 1000;

    /**
     * 生成访问令牌
     */
    public static String generateAccessToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, ACCESS_TOKEN_EXPIRE);
    }

    /**
     * 生成刷新令牌
     */
    public static String generateRefreshToken(String subject) {
        return generateToken(subject, null, REFRESH_TOKEN_EXPIRE);
    }

    /**
     * 生成令牌
     */
    private static String generateToken(String subject, Map<String, Object> claims, long expire) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(KEY);

        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 解析令牌
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.error("Token格式错误: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.error("Token非法: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 验证令牌是否有效
     */
    public static boolean validateToken(String token) {
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
    public static String getSubject(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从令牌中获取声明
     */
    @SuppressWarnings("unchecked")
    public static <T> T getClaim(String token, String claim) {
        Claims claims = parseToken(token);
        return (T) claims.get(claim);
    }

    /**
     * 获取令牌过期时间
     */
    public static Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 判断令牌是否即将过期（小于30分钟）
     */
    public static boolean isTokenExpired(String token) {
        Date expiration = getExpiration(token);
        return expiration.before(new Date(System.currentTimeMillis() + 30 * 60 * 1000));
    }
}
