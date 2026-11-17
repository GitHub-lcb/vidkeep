package com.vidkeep.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 站点访问口令（P1 F-18，文档 2.1 F-18：全站需要输入密码才能访问，内网场景）。
 *
 * <p>口令为空时功能关闭。通过口令后签发 HMAC 签名令牌（HttpOnly cookie），
 * 令牌格式：{@code expireEpochSeconds.signature}。管理员 JWT 不受此限制。</p>
 */
@Component
public class SiteAccessService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final boolean enabled;
    private final byte[] accessPassword;
    private final SecretKeySpec key;
    private final long tokenExpireSeconds;
    private final String cookieName;

    public SiteAccessService(@Value("${app.site.access-password:}") String accessPassword,
                             @Value("${app.site.token-secret:}") String tokenSecret,
                             @Value("${app.site.token-expire-seconds:2592000}") long tokenExpireSeconds,
                             @Value("${app.site.cookie-name:vidkeep_site_token}") String cookieName) {
        this.enabled = accessPassword != null && !accessPassword.isBlank();
        this.accessPassword = (accessPassword == null ? "" : accessPassword).getBytes(StandardCharsets.UTF_8);
        this.tokenExpireSeconds = tokenExpireSeconds;
        this.cookieName = cookieName;
        if (this.enabled) {
            if (tokenSecret == null || tokenSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "启用站点访问口令时，app.site.token-secret 至少 32 字节（配置 SITE_TOKEN_SECRET 或 JWT_SECRET）");
            }
            this.key = new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        } else {
            this.key = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCookieName() {
        return cookieName;
    }

    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }

    /**
     * 口令比对（常量时间）。
     */
    public boolean passwordMatches(String rawPassword) {
        if (!enabled || rawPassword == null) {
            return false;
        }
        return MessageDigest.isEqual(accessPassword, rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发访问口令令牌。
     */
    public String issueToken() {
        if (!enabled) {
            return "";
        }
        long expireAt = Instant.now().getEpochSecond() + tokenExpireSeconds;
        String payload = String.valueOf(expireAt);
        return payload + "." + signature(payload);
    }

    /**
     * 校验令牌：格式、过期时间、签名。
     */
    public boolean verifyToken(String token) {
        if (!enabled || token == null || token.isBlank()) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        String payload = token.substring(0, dot);
        String provided = token.substring(dot + 1);
        long expireAt;
        try {
            expireAt = Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return false;
        }
        if (expireAt < Instant.now().getEpochSecond()) {
            return false;
        }
        String expected = signature(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("站点口令令牌签名失败", e);
        }
    }
}
