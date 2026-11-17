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
 * 播放地址签名（P1 防盗链，文档 7.3.4）。
 *
 * <p>签名算法：{@code token = HMAC-SHA256(secret, path + "|" + expire)}，32 位小写十六进制。
 * 有效期默认 2 小时；m3u8 改写出的子列表/切片使用更长的有效期，避免长视频播放中途失效。</p>
 */
@Component
public class PlayTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final long expireSeconds;
    private final long segmentExpireSeconds;

    public PlayTokenService(@Value("${app.play.token-secret:}") String secret,
                            @Value("${app.play.token-expire-seconds:7200}") long expireSeconds,
                            @Value("${app.play.segment-expire-seconds:43200}") long segmentExpireSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.play.token-secret 长度不足，请通过环境变量 PLAY_TOKEN_SECRET 提供至少 32 字节密钥");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.expireSeconds = expireSeconds;
        this.segmentExpireSeconds = segmentExpireSeconds;
    }

    /**
     * 生成带签名与过期时间的播放地址。
     */
    public String buildSignedUrl(String path) {
        long expireAt = Instant.now().getEpochSecond() + expireSeconds;
        return path + "?expire=" + expireAt + "&token=" + sign(path, expireAt);
    }

    /**
     * 为 m3u8 内部路径生成带签名地址（使用切片级有效期）。
     */
    public String buildSignedSegmentUrl(String path) {
        long expireAt = Instant.now().getEpochSecond() + segmentExpireSeconds;
        return path + "?expire=" + expireAt + "&token=" + sign(path, expireAt);
    }

    /**
     * 校验请求路径的 expire/token 是否合法且未过期。
     */
    public boolean verify(String path, String expire, String token) {
        if (path == null || expire == null || token == null || token.isBlank()) {
            return false;
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(expire);
        } catch (NumberFormatException e) {
            return false;
        }
        if (expireAt < Instant.now().getEpochSecond()) {
            return false;
        }
        String expected = sign(path, expireAt);
        // 常量时间比较，避免时序侧信道
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String path, long expireAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((path + "|" + expireAt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("播放地址签名失败", e);
        }
    }
}
