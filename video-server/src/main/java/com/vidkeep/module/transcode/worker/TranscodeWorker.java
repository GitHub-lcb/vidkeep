package com.vidkeep.module.transcode.worker;

import com.vidkeep.infra.queue.TranscodeQueue;
import com.vidkeep.module.transcode.service.TranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 转码 Worker（文档 7.2.6 / 7.2.7）。
 *
 * <p>只在 app.transcode.worker-enabled=true 时启用：dev/worker profile 启用，prod 的 app 容器关闭，
 * 由 compose 中的 worker 容器消费队列。</p>
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.transcode.worker-enabled", havingValue = "true", matchIfMissing = true)
public class TranscodeWorker {

    private static final Logger log = LoggerFactory.getLogger(TranscodeWorker.class);

    private final TranscodeQueue transcodeQueue;
    private final TranscodeService transcodeService;
    private final String consumerName = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    public TranscodeWorker(TranscodeQueue transcodeQueue, TranscodeService transcodeService) {
        this.transcodeQueue = transcodeQueue;
        this.transcodeService = transcodeService;
    }

    /**
     * 启动时回收僵尸任务（PROCESSING 超过 2 小时）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            transcodeService.recoverZombieTasks();
        } catch (RuntimeException e) {
            log.error("启动时回收僵尸转码任务失败", e);
        }
    }

    /**
     * 轮询转码队列，一次处理一个任务；单线程即可满足当前规模（TRANSCODE_CONCURRENCY=1）。
     */
    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<TranscodeQueue.QueuedTask> tasks;
        try {
            tasks = transcodeQueue.poll(consumerName, 1, Duration.ofSeconds(2));
        } catch (RuntimeException e) {
            log.error("读取转码队列失败", e);
            return;
        }
        for (TranscodeQueue.QueuedTask task : tasks) {
            try {
                transcodeService.run(task.episodeId(), task.coverMode());
            } catch (RuntimeException e) {
                log.error("转码任务执行失败：episodeId={}", task.episodeId(), e);
                try {
                    transcodeService.markFailed(task.episodeId(), e.getMessage());
                } catch (RuntimeException markError) {
                    log.error("标记转码失败状态时出错：episodeId={}", task.episodeId(), markError);
                }
            } finally {
                try {
                    transcodeQueue.ack(task.messageId());
                } catch (RuntimeException e) {
                    log.warn("ACK 转码消息失败：messageId={}", task.messageId(), e);
                }
            }
        }
    }
}
