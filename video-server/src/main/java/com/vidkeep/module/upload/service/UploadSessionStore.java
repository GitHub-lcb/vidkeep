package com.vidkeep.module.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 上传会话 Redis 存储（文档 7.1.3）。
 *
 * <p>两个 key：</p>
 * <ul>
 *   <li>{@code upload:session:{uploadId}} → 会话 JSON</li>
 *   <li>{@code upload:fingerprint:{fingerprint}} → uploadId（用于刷新页面后按指纹续传）</li>
 * </ul>
 */
@Component
public class UploadSessionStore {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionStore.class);

    private static final String SESSION_KEY_PREFIX = "upload:session:";
    private static final String FINGERPRINT_KEY_PREFIX = "upload:fingerprint:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UploadSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(UploadSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + session.uploadId(), json, TTL);
            redisTemplate.opsForValue().set(FINGERPRINT_KEY_PREFIX + session.fingerprint(), session.uploadId(), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("上传会话序列化失败", e);
        }
    }

    public UploadSession get(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + uploadId);
        return parse(json);
    }

    /**
     * 按指纹查找未完成的上传会话（刷新浏览器后续传）。
     */
    public UploadSession findByFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        String uploadId = redisTemplate.opsForValue().get(FINGERPRINT_KEY_PREFIX + fingerprint);
        return uploadId == null ? null : get(uploadId);
    }

    public void delete(UploadSession session) {
        if (session == null) {
            return;
        }
        redisTemplate.delete(SESSION_KEY_PREFIX + session.uploadId());
        redisTemplate.delete(FINGERPRINT_KEY_PREFIX + session.fingerprint());
    }

    private UploadSession parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UploadSession.class);
        } catch (JsonProcessingException e) {
            log.warn("上传会话反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
