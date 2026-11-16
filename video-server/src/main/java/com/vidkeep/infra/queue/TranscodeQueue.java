package com.vidkeep.infra.queue;

import java.time.Duration;
import java.util.List;

/**
 * 转码任务队列抽象（当前实现：Redis Stream；后续可替换为其他 MQ）。
 */
public interface TranscodeQueue {

    /**
     * 投递转码任务。
     *
     * @param episodeId 剧集 ID
     * @param coverMode 封面模式：AUTO 自动截帧 / NONE 不生成
     */
    void enqueue(Long episodeId, String coverMode);

    /**
     * 以消费者组方式拉取任务；无任务时阻塞至多 block。
     */
    List<QueuedTask> poll(String consumerName, int count, Duration block);

    /**
     * 确认消费成功。
     */
    void ack(String messageId);

    /**
     * 队列消息。
     *
     * @param messageId Redis Stream 消息 ID
     * @param episodeId 剧集 ID
     * @param coverMode 封面模式
     */
    record QueuedTask(String messageId, Long episodeId, String coverMode) {
    }
}
