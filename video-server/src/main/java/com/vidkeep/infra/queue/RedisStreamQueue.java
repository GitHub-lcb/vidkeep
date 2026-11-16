package com.vidkeep.infra.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream 队列实现：XADD / XREADGROUP / XACK。
 *
 * <p>文档 3.3 / 7.2.6：不引入 RabbitMQ，低频单消费者场景用 Redis Stream 足够。</p>
 */
@Component
public class RedisStreamQueue implements TranscodeQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamQueue.class);

    private static final String STREAM_KEY = "transcode:queue";
    private static final String GROUP = "transcode-group";
    private static final String FIELD_EPISODE_ID = "episodeId";
    private static final String FIELD_COVER_MODE = "coverMode";

    private final StringRedisTemplate redisTemplate;

    public RedisStreamQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 消费者组只在首次使用时创建；创建成功后不再重复尝试 */
    private volatile boolean groupReady = false;

    @Override
    public void enqueue(Long episodeId, String coverMode) {
        Map<String, String> body = Map.of(
                FIELD_EPISODE_ID, String.valueOf(episodeId),
                FIELD_COVER_MODE, coverMode == null ? "AUTO" : coverMode);
        RecordId recordId = redisTemplate.opsForStream().add(STREAM_KEY, body);
        log.info("转码任务入队：episodeId={}, coverMode={}, messageId={}", episodeId, coverMode, recordId);
        ensureGroup();
    }

    @Override
    public List<QueuedTask> poll(String consumerName, int count, Duration block) {
        ensureGroup();
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(count).block(block),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<QueuedTask> tasks = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Object episodeIdValue = record.getValue().get(FIELD_EPISODE_ID);
            if (episodeIdValue == null) {
                log.warn("忽略缺少 episodeId 的队列消息：{}", record.getId());
                continue;
            }
            Object coverModeValue = record.getValue().get(FIELD_COVER_MODE);
            tasks.add(new QueuedTask(
                    record.getId().getValue(),
                    Long.parseLong(episodeIdValue.toString()),
                    coverModeValue == null ? "AUTO" : coverModeValue.toString()));
        }
        return tasks;
    }

    @Override
    public void ack(String messageId) {
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, messageId);
    }

    /**
     * 消费者组不存在时创建；Redis 返回 BUSYGROUP 说明已存在，静默处理。
     */
    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP);
            log.info("已创建转码消费者组：{}", GROUP);
            groupReady = true;
        } catch (RuntimeException e) {
            if (isBusyGroup(e)) {
                groupReady = true;
                return;
            }
            log.warn("创建转码消费者组失败：{}", e.getMessage());
        }
    }

    /**
     * 判断异常链中是否包含 BUSYGROUP（组已存在）。
     */
    private boolean isBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
