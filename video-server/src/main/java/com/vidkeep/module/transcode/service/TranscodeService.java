package com.vidkeep.module.transcode.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.infra.ffmpeg.FfmpegService;
import com.vidkeep.infra.ffmpeg.FfprobeService;
import com.vidkeep.infra.ffmpeg.ProbeResult;
import com.vidkeep.infra.ffmpeg.TranscodeDecider;
import com.vidkeep.infra.ffmpeg.TranscodeDecision;
import com.vidkeep.infra.ffmpeg.TranscodeProfile;
import com.vidkeep.infra.queue.TranscodeQueue;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.module.transcode.dto.TranscodeProgressResponse;
import com.vidkeep.module.transcode.entity.TranscodeTask;
import com.vidkeep.module.transcode.mapper.TranscodeTaskMapper;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 转码服务：状态机、ffprobe 探测、REMUX/TRANSCODE 执行、进度更新、失败重试、取消、僵尸任务回收。
 *
 * <p>状态机（AGENTS 4.3）：NONE → PENDING → PROCESSING → DONE / FAILED（FAILED 可重试 ≤3 次）。</p>
 */
@Service
public class TranscodeService {

    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_RUNNING = "RUNNING";
    private static final String TASK_SUCCESS = "SUCCESS";
    private static final String TASK_FAILED = "FAILED";

    private static final String PROGRESS_KEY_PREFIX = "transcode:progress:";
    private static final Duration PROGRESS_TTL = Duration.ofHours(24);
    private static final int MAX_RETRY = 3;
    private static final int DB_PROGRESS_STEP = 5;
    private static final int ZOMBIE_HOURS = 2;

    private final VideoMapper videoMapper;
    private final VideoEpisodeMapper episodeMapper;
    private final TranscodeTaskMapper taskMapper;
    private final StorageService storageService;
    private final FfprobeService ffprobeService;
    private final FfmpegService ffmpegService;
    private final TranscodeDecider transcodeDecider;
    private final TranscodeQueue transcodeQueue;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 已请求取消的剧集（运行中的进程会被强制结束） */
    private final Set<Long> canceledEpisodes = ConcurrentHashMap.newKeySet();

    public TranscodeService(VideoMapper videoMapper,
                            VideoEpisodeMapper episodeMapper,
                            TranscodeTaskMapper taskMapper,
                            StorageService storageService,
                            FfprobeService ffprobeService,
                            FfmpegService ffmpegService,
                            TranscodeDecider transcodeDecider,
                            TranscodeQueue transcodeQueue,
                            StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper) {
        this.videoMapper = videoMapper;
        this.episodeMapper = episodeMapper;
        this.taskMapper = taskMapper;
        this.storageService = storageService;
        this.ffprobeService = ffprobeService;
        this.ffmpegService = ffmpegService;
        this.transcodeDecider = transcodeDecider;
        this.transcodeQueue = transcodeQueue;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询转码进度（优先读 Redis 实时进度，回退数据库）。
     */
    public TranscodeProgressResponse queryProgress(Long episodeId) {
        VideoEpisode episode = requireEpisode(episodeId);
        String json = redisTemplate.opsForValue().get(progressKey(episodeId));
        if (json != null) {
            try {
                return objectMapper.readValue(json, TranscodeProgressResponse.class);
            } catch (JsonProcessingException e) {
                log.debug("转码进度缓存解析失败，回退数据库：{}", e.getMessage());
            }
        }
        TranscodeTask task = latestTask(episodeId);
        return new TranscodeProgressResponse(
                episodeId,
                episode.getTranscodeStatus(),
                task == null || task.getProgress() == null ? 0 : task.getProgress(),
                episode.getTranscodeMode(),
                task == null ? null : task.getErrorMsg());
    }

    /**
     * 重试失败/未开始的转码（最多 3 次）。
     */
    public void retry(Long episodeId) {
        VideoEpisode episode = requireEpisode(episodeId);
        if (STATUS_PROCESSING.equals(episode.getTranscodeStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "转码进行中，请先取消再重试");
        }
        if (STATUS_DONE.equals(episode.getTranscodeStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "转码已完成，无需重试");
        }

        TranscodeTask task = findOrCreateTask(episodeId);
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= MAX_RETRY) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "重试次数已达上限（" + MAX_RETRY + " 次）");
        }

        canceledEpisodes.remove(episodeId);
        int nextRetry = retryCount + 1;
        // 需要把 errorMsg/finishedAt 置空，使用 UpdateWrapper（updateById 会忽略 null 字段）
        taskMapper.update(null, Wrappers.<TranscodeTask>lambdaUpdate()
                .eq(TranscodeTask::getId, task.getId())
                .set(TranscodeTask::getStatus, TASK_PENDING)
                .set(TranscodeTask::getProgress, 0)
                .set(TranscodeTask::getRetryCount, nextRetry)
                .set(TranscodeTask::getErrorMsg, null)
                .set(TranscodeTask::getStartedAt, null)
                .set(TranscodeTask::getFinishedAt, null));
        episode.setTranscodeStatus(STATUS_PENDING);
        episodeMapper.updateById(episode);
        transcodeQueue.enqueue(episodeId, "AUTO");
        writeProgress(episodeId, STATUS_PENDING, 0, episode.getTranscodeMode(), null);
        log.info("重试转码：episodeId={}, retryCount={}", episodeId, nextRetry);
    }

    /**
     * 取消转码：终止运行中的 FFmpeg 进程，任务置为 FAILED（errorMsg=已取消）。
     */
    public void cancel(Long episodeId) {
        VideoEpisode episode = requireEpisode(episodeId);
        if (STATUS_DONE.equals(episode.getTranscodeStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "转码已完成，无法取消");
        }
        canceledEpisodes.add(episodeId);
        ffmpegService.cancel(episodeId);

        episode.setTranscodeStatus(STATUS_FAILED);
        episodeMapper.updateById(episode);

        TranscodeTask task = latestTask(episodeId);
        int progress = 0;
        String mode = episode.getTranscodeMode();
        if (task != null) {
            progress = task.getProgress() == null ? 0 : task.getProgress();
            task.setStatus(TASK_FAILED);
            task.setErrorMsg("已取消");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        writeProgress(episodeId, STATUS_FAILED, progress, mode, "已取消");
        log.info("取消转码：episodeId={}", episodeId);
    }

    /**
     * 执行一次转码（由 Worker 调用）。失败时抛出异常，由调用方标记失败。
     */
    public void run(Long episodeId, String coverMode) {
        VideoEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            log.warn("剧集不存在，跳过转码：episodeId={}", episodeId);
            return;
        }
        if (STATUS_DONE.equals(episode.getTranscodeStatus())) {
            log.info("转码已完成，跳过：episodeId={}", episodeId);
            return;
        }
        if (canceledEpisodes.remove(episodeId)) {
            log.info("转码已取消，跳过队列消息：episodeId={}", episodeId);
            return;
        }

        TranscodeTask task = findOrCreateTask(episodeId);
        task.setStatus(TASK_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setProgress(0);
        taskMapper.updateById(task);

        episode.setTranscodeStatus(STATUS_PROCESSING);
        episodeMapper.updateById(episode);
        writeProgress(episodeId, STATUS_PROCESSING, 0, episode.getTranscodeMode(), null);
        log.info("开始转码：episodeId={}, storageKey={}", episodeId, episode.getStorageKey());

        Path input = storageService.resolve(episode.getStorageKey());
        if (!Files.isRegularFile(input)) {
            throw new BizException(ErrorCode.NOT_FOUND, "原片不存在：" + episode.getStorageKey());
        }

        ProbeResult probe = ffprobeService.probe(input);
        episode.setDuration((int) Math.round(probe.durationSeconds()));
        episode.setWidth(probe.width());
        episode.setHeight(probe.height());
        episode.setBitRate((int) Math.min(Integer.MAX_VALUE, probe.bitRate()));
        episodeMapper.updateById(episode);

        TranscodeDecision decision = transcodeDecider.decide(probe);
        String hlsDirKey = "hls/" + episodeId;
        // 幂等：先清理历史产物，避免失败重试产生重复文件
        storageService.delete(hlsDirKey);
        storageService.ensureDirectory(hlsDirKey);
        Path hlsDir = storageService.resolve(hlsDirKey);

        episode.setTranscodeMode(decision.mode().name());
        episodeMapper.updateById(episode);

        List<TranscodeProfile> profiles = decision.profiles();
        try {
            execute(episodeId, input, hlsDir, probe, decision);
        } catch (BizException e) {
            if (canceledEpisodes.contains(episodeId)) {
                throw e;
            }
            if (decision.mode() == TranscodeDecision.TranscodeMode.REMUX) {
                // REMUX 失败常见于音频不是 AAC 的 MP4，回退重编码保证可用
                log.warn("REMUX 失败，回退 TRANSCODE：episodeId={}, err={}", episodeId, e.getMessage());
                TranscodeDecision fallback = new TranscodeDecision(
                        TranscodeDecision.TranscodeMode.TRANSCODE,
                        List.of(TranscodeProfile.P720, TranscodeProfile.P480));
                storageService.delete(hlsDirKey);
                storageService.ensureDirectory(hlsDirKey);
                episode.setTranscodeMode(fallback.mode().name());
                episodeMapper.updateById(episode);
                execute(episodeId, input, hlsDir, probe, fallback);
                decision = fallback;
                profiles = fallback.profiles();
            } else {
                throw e;
            }
        }

        ffmpegService.writeMasterPlaylist(hlsDir, probe, profiles);

        // 自动封面：作品没有封面且请求 coverMode=AUTO 时截帧（F-10）
        if ("AUTO".equalsIgnoreCase(coverMode)) {
            generateCoverIfAbsent(episode, input, probe.durationSeconds());
        }

        episode.setTranscodeStatus(STATUS_DONE);
        episode.setHlsMasterUrl(hlsDirKey + "/master.m3u8");
        episodeMapper.updateById(episode);

        task.setStatus(TASK_SUCCESS);
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        writeProgress(episodeId, STATUS_DONE, 100, episode.getTranscodeMode(), null);
        log.info("转码完成：episodeId={}, mode={}, duration={}s", episodeId, episode.getTranscodeMode(),
                episode.getDuration());
    }

    /**
     * 标记转码失败（Worker 捕获异常后调用）。
     */
    public void markFailed(Long episodeId, String message) {
        VideoEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            return;
        }
        String error = message == null || message.isBlank() ? "未知错误" : message;
        if (canceledEpisodes.remove(episodeId)) {
            error = "已取消";
        }
        if (error.length() > 1024) {
            error = error.substring(0, 1024);
        }
        episode.setTranscodeStatus(STATUS_FAILED);
        episodeMapper.updateById(episode);

        TranscodeTask task = latestTask(episodeId);
        int progress = 0;
        if (task != null) {
            progress = task.getProgress() == null ? 0 : task.getProgress();
            task.setStatus(TASK_FAILED);
            task.setErrorMsg(error);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        writeProgress(episodeId, STATUS_FAILED, progress, episode.getTranscodeMode(), error);
        log.error("转码失败：episodeId={}, error={}", episodeId, error);
    }

    /**
     * Worker 启动时回收僵死任务：PROCESSING 超过 2 小时的重新排队（AGENTS 4.3）。
     */
    public void recoverZombieTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(ZOMBIE_HOURS);
        List<VideoEpisode> zombies = episodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getTranscodeStatus, STATUS_PROCESSING)
                .lt(VideoEpisode::getUpdatedAt, threshold));
        for (VideoEpisode episode : zombies) {
            log.warn("回收僵尸转码任务：episodeId={}", episode.getId());
            episode.setTranscodeStatus(STATUS_PENDING);
            episodeMapper.updateById(episode);
            TranscodeTask task = latestTask(episode.getId());
            if (task != null) {
                task.setStatus(TASK_PENDING);
                taskMapper.updateById(task);
            }
            transcodeQueue.enqueue(episode.getId(), "AUTO");
        }
        if (!zombies.isEmpty()) {
            log.info("僵尸任务回收完成：count={}", zombies.size());
        }
    }

    /**
     * 执行 FFmpeg（REMUX 或逐档 TRANSCODE），并绑定进度回调。
     *
     * <p>多档转码时把每档进度聚合为整体进度，保证前端看到的百分比单调递增。</p>
     */
    private void execute(Long episodeId, Path input, Path hlsDir, ProbeResult probe, TranscodeDecision decision) {
        TranscodeTask task = latestTask(episodeId);
        Long taskId = task == null ? null : task.getId();
        FfmpegService.ProgressListener listener =
                createProgressListener(episodeId, taskId, decision.mode().name());
        if (decision.mode() == TranscodeDecision.TranscodeMode.REMUX) {
            ffmpegService.remux(episodeId, input, hlsDir, probe.durationSeconds(), listener);
            return;
        }

        List<TranscodeProfile> profiles = decision.profiles();
        int profileCount = profiles.size();
        for (int i = 0; i < profileCount; i++) {
            final int profileIndex = i;
            FfmpegService.ProgressListener profileListener = percent -> {
                // 整体进度 = (已完成档位 + 当前档位进度) / 总档位数
                double overall = (profileIndex + Math.min(percent, 100.0) / 100.0) / profileCount * 100.0;
                listener.onProgress(overall);
            };
            ffmpegService.transcode(episodeId, input, hlsDir, profiles.get(i), probe.durationSeconds(),
                    profileListener);
        }
    }

    /**
     * 进度回调：Redis 每秒级更新，数据库每 5% 更新一次（文档 7.2.5）。
     */
    private FfmpegService.ProgressListener createProgressListener(Long episodeId, Long taskId, String mode) {
        AtomicInteger lastPercent = new AtomicInteger(-1);
        AtomicInteger lastDbBucket = new AtomicInteger(-1);
        return percent -> {
            int current = (int) Math.floor(percent);
            if (current == lastPercent.get()) {
                return;
            }
            lastPercent.set(current);
            writeProgress(episodeId, STATUS_PROCESSING, current, mode, null);

            int bucket = current / DB_PROGRESS_STEP;
            if (bucket > lastDbBucket.get()) {
                lastDbBucket.set(bucket);
                if (taskId != null) {
                    TranscodeTask update = new TranscodeTask();
                    update.setId(taskId);
                    update.setProgress(current);
                    taskMapper.updateById(update);
                }
            }
        };
    }

    private void generateCoverIfAbsent(VideoEpisode episode, Path input, double durationSeconds) {
        Video video = videoMapper.selectById(episode.getVideoId());
        if (video == null || (video.getCoverUrl() != null && !video.getCoverUrl().isBlank())) {
            return;
        }
        int atSecond = (int) Math.min(60, Math.max(1, durationSeconds / 2));
        String coverKey = "covers/" + video.getId() + ".jpg";
        boolean ok = ffmpegService.extractCover(input, storageService.resolve(coverKey), atSecond);
        if (ok) {
            video.setCoverUrl("/" + coverKey);
            videoMapper.updateById(video);
            log.info("自动生成封面：videoId={}, key={}, at={}s", video.getId(), coverKey, atSecond);
        }
    }

    private void writeProgress(Long episodeId, String status, int progress, String mode, String errorMsg) {
        TranscodeProgressResponse response = new TranscodeProgressResponse(
                episodeId, status, progress, mode, errorMsg);
        try {
            redisTemplate.opsForValue().set(progressKey(episodeId),
                    objectMapper.writeValueAsString(response), PROGRESS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("写入转码进度缓存失败：{}", e.getMessage());
        }
    }

    private String progressKey(Long episodeId) {
        return PROGRESS_KEY_PREFIX + episodeId;
    }

    private VideoEpisode requireEpisode(Long episodeId) {
        VideoEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "剧集不存在");
        }
        return episode;
    }

    private TranscodeTask latestTask(Long episodeId) {
        return taskMapper.selectOne(Wrappers.<TranscodeTask>lambdaQuery()
                .eq(TranscodeTask::getEpisodeId, episodeId)
                .orderByDesc(TranscodeTask::getId)
                .last("LIMIT 1"));
    }

    private TranscodeTask findOrCreateTask(Long episodeId) {
        TranscodeTask task = latestTask(episodeId);
        if (task != null) {
            return task;
        }
        task = new TranscodeTask();
        task.setEpisodeId(episodeId);
        task.setStatus(TASK_PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        taskMapper.insert(task);
        return task;
    }
}
