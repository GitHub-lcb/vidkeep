package com.vidkeep.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试：签发、解析、过期与篡改。
 */
class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3600);

    @Test
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken(1L, "ADMIN");

        Claims claims = jwtUtil.parseToken(token);

        assertEquals(1L, jwtUtil.getUserId(claims));
        assertEquals("ADMIN", jwtUtil.getRole(claims));
        assertEquals(3600L, jwtUtil.getExpireSeconds());
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -10);
        String token = expiredJwtUtil.generateToken(1L, "ADMIN");

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtUtil.generateToken(1L, "ADMIN");

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(token + "tampered"));
    }

    @Test
    void shouldRejectSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short-secret", 3600));
    }
}
