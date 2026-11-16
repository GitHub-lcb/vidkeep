package com.vidkeep.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析（HS256）。
 *
 * <p>payload 约定：sub=userId，role=ADMIN/USER，exp=过期时间；默认有效期 7 天。</p>
 */
@Component
public class JwtUtil {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-seconds:604800}") long expireSeconds) {
        // HS256 要求密钥至少 256 位（32 字节）
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足，请通过环境变量 JWT_SECRET 提供至少 32 字节的密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /**
     * 签发 token。
     *
     * @param userId 用户 ID
     * @param role   角色（ADMIN / USER）
     */
    public String generateToken(Long userId, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 token；签名错误、过期等都会抛 {@link io.jsonwebtoken.JwtException}。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getRole(Claims claims) {
        Object role = claims.get(CLAIM_ROLE);
        return role == null ? null : role.toString();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
