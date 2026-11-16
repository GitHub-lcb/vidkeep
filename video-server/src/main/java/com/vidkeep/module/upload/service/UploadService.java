package com.vidkeep.module.upload.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.infra.queue.TranscodeQueue;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.module.transcode.entity.TranscodeTask;
import com.vidkeep.module.transcode.mapper.TranscodeTaskMapper;
import com.vidkeep.module.upload.dto.UploadCheckRequest;
import com.vidkeep.module.upload.dto.UploadCheckResponse;
import com.vidkeep.module.upload.dto.UploadChunkResponse;
import com.vidkeep.module.upload.dto.UploadCompleteRequest;
import com.vidkeep.module.upload.dto.UploadCompleteResponse;
import com.vidkeep.module.upload.dto.UploadInitRequest;
import com.vidkeep.module.upload.dto.UploadInitResponse;
import com.vidkeep.module.upload.dto.UploadStatusResponse;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 分片上传服务（文档 7.1）：秒传检查、断点续传、分片合并、MD5 校验、入库入队。
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    /** uploadId 由服务端生成（UUID 去横线），自定义格式一律拒绝，避免拼进路径 */
    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");
    private static final String TMP_DIR = "tmp";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter UPLOAD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final StorageService storageService;
    private final UploadSessionStore sessionStore;
    private final FileTypeValidator fileTypeValidator;
    private final VideoMapper videoMapper;
    private final VideoEpisodeMapper episodeMapper;
    private final TranscodeTaskMapper transcodeTaskMapper;
    private final TranscodeQueue transcodeQueue;
    private final int chunkSize;
    private final long maxFileSize;

    public UploadService(StorageService storageService,
                         UploadSessionStore sessionStore,
                         FileTypeValidator fileTypeValidator,
                         VideoMapper videoMapper,
                         VideoEpisodeMapper episodeMapper,
                         TranscodeTaskMapper transcodeTaskMapper,
                         TranscodeQueue transcodeQueue,
                         @Value("${app.upload.chunk-size-bytes:5242880}") int chunkSize,
                         @Value("${app.upload.max-file-size-bytes:10737418240}") long maxFileSize) {
        this.storageService = storageService;
        this.sessionStore = sessionStore;
        this.fileTypeValidator = fileTypeValidator;
        this.videoMapper = videoMapper;
        this.episodeMapper = episodeMapper;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.transcodeQueue = transcodeQueue;
        this.chunkSize = chunkSize;
        this.maxFileSize = maxFileSize;
    }

    /**
     * 秒传检查 / 续传会话查询（文档 6.3.2）。
     */
    public UploadCheckResponse check(UploadCheckRequest request) {
        String fingerprint = normalizeFingerprint(request.fingerprint());
        fileTypeValidator.validateExtension(request.fileName());
        validateFileSize(request.fileSize());

        // 秒传：同指纹文件已存在且原片仍在磁盘上
        VideoEpisode existing = episodeMapper.selectOne(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getFileFingerprint, fingerprint)
                .last("LIMIT 1"));
        if (existing != null && existing.getStorageKey() != null && storageService.exists(existing.getStorageKey())) {
            log.info("秒传命中：fingerprint={}, episodeId={}", fingerprint, existing.getId());
            return UploadCheckResponse.hit(existing.getId());
        }

        // 续传：按指纹找未过期的上传会话
        UploadSession session = sessionStore.findByFingerprint(fingerprint);
        if (session != null && session.fileSize() == request.fileSize()) {
            return UploadCheckResponse.miss(session.uploadId(), session.chunkSize(),
                    session.totalChunks(), storageService.listUploadedChunks(session.uploadId()));
        }
        return UploadCheckResponse.notStarted();
    }

    /**
     * 初始化上传会话（文档 6.3.3）。相同指纹的会话已存在时直接复用，保证幂等。
     */
    public UploadInitResponse init(UploadInitRequest request) {
        String fingerprint = normalizeFingerprint(request.fingerprint());
        fileTypeValidator.validateExtension(request.fileName());
        validateFileSize(request.fileSize());

        UploadSession existing = sessionStore.findByFingerprint(fingerprint);
        if (existing != null && existing.fileSize() == request.fileSize()) {
            return new UploadInitResponse(existing.uploadId(), existing.chunkSize(), existing.totalChunks());
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int totalChunks = (int) ((request.fileSize() + chunkSize - 1) / chunkSize);
        UploadSession session = new UploadSession(uploadId, request.fileName(), request.fileSize(),
                fingerprint, chunkSize, totalChunks, System.currentTimeMillis());
        sessionStore.save(session);
        log.info("初始化上传会话：uploadId={}, fileName={}, fileSize={}, totalChunks={}",
                uploadId, request.fileName(), request.fileSize(), totalChunks);
        return new UploadInitResponse(uploadId, chunkSize, totalChunks);
    }

    /**
     * 上传分片（文档 6.3.4）：覆盖写幂等，并校验分片大小。
     */
    public UploadChunkResponse uploadChunk(String uploadId, int index, InputStream inputStream) {
        UploadSession session = requireSession(uploadId);
        if (index < 0 || index >= session.totalChunks()) {
            throw new BizException(ErrorCode.CHUNK_INDEX_OUT_OF_RANGE,
                    "分片序号越界：[0," + (session.totalChunks() - 1) + "]");
        }
        storageService.writeChunk(uploadId, index, inputStream);

        long expectedSize = expectedChunkSize(session, index);
        long actualSize = storageService.size(TMP_DIR + "/" + uploadId + "/" + index + ".part");
        if (actualSize != expectedSize) {
            // 网络中断产生的残缺分片直接删掉，等待前端重传
            storageService.delete(TMP_DIR + "/" + uploadId + "/" + index + ".part");
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "分片大小不正确：期望 " + expectedSize + " 字节，实际 " + actualSize + " 字节");
        }
        return new UploadChunkResponse(index, true);
    }

    /**
     * 已上传分片列表（断点续传）。
     */
    public UploadStatusResponse status(String uploadId) {
        UploadSession session = requireSession(uploadId);
        return new UploadStatusResponse(session.uploadId(), session.chunkSize(), session.totalChunks(),
                storageService.listUploadedChunks(uploadId));
    }

    /**
     * 完成上传（文档 6.3.5）：合并 → 大小/MD5/魔数校验 → 建 episode → 建转码任务 → 入队 → 清理临时分片。
     */
    public UploadCompleteResponse complete(String uploadId, UploadCompleteRequest request) {
        UploadSession session = requireSession(uploadId);
        Video video = videoMapper.selectById(request.videoId());
        if (video == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        int episodeNo = resolveEpisodeNo(request);
        String title = resolveTitle(request.title(), episodeNo);

        // 磁盘空间检查：预留 20% 余量（文档 7.1.5）
        long usable = storageService.usableSpace();
        if (usable < session.fileSize() * 1.2) {
            log.warn("磁盘空间不足：usable={}, need≈{}", usable, session.fileSize());
            throw new BizException(ErrorCode.DISK_FULL);
        }

        String extension = fileTypeValidator.validateExtension(session.fileName());
        String datePath = LocalDate.now(ZONE).format(UPLOAD_DATE_FORMAT);
        String targetKey = "uploads/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        String md5 = storageService.mergeChunks(uploadId, session.totalChunks(), targetKey);
        VideoEpisode episode = null;
        TranscodeTask task = null;
        try {
            long mergedSize = storageService.size(targetKey);
            if (mergedSize != session.fileSize()) {
                throw new BizException(ErrorCode.MERGE_FAILED,
                        "合并后文件大小不一致：期望 " + session.fileSize() + "，实际 " + mergedSize);
            }
            // 魔数校验：防止改后缀上传非视频文件
            fileTypeValidator.validateMagic(storageService.resolve(targetKey));

            episode = new VideoEpisode();
            episode.setVideoId(video.getId());
            episode.setEpisodeNo(episodeNo);
            episode.setTitle(title);
            episode.setPlayType("HLS_LOCAL");
            episode.setFileSize(session.fileSize());
            episode.setFileFingerprint(session.fingerprint());
            episode.setFileMd5(md5);
            episode.setStorageKey(targetKey);
            episode.setTranscodeStatus("PENDING");
            episodeMapper.insert(episode);

            task = new TranscodeTask();
            task.setEpisodeId(episode.getId());
            task.setStatus("PENDING");
            task.setProgress(0);
            task.setRetryCount(0);
            transcodeTaskMapper.insert(task);

            try {
                transcodeQueue.enqueue(episode.getId(), request.coverModeOrDefault());
            } catch (RuntimeException e) {
                // 入队失败回滚刚创建的剧集与任务，避免留下永远 PENDING 的孤儿数据
                log.error("转码任务入队失败，回滚剧集：episodeId={}", episode.getId(), e);
                episodeMapper.deleteById(episode.getId());
                transcodeTaskMapper.delete(Wrappers.<TranscodeTask>lambdaQuery()
                        .eq(TranscodeTask::getEpisodeId, episode.getId()));
                throw new BizException(ErrorCode.TRANSCODE_TASK_FAILED, "转码任务创建失败，请重试");
            }

            sessionStore.delete(session);
            storageService.cleanChunks(uploadId);
            log.info("上传完成：videoId={}, episodeId={}, episodeNo={}, size={}, md5={}, key={}",
                    video.getId(), episode.getId(), episodeNo, session.fileSize(), md5, targetKey);
            return new UploadCompleteResponse(episode.getId(), "PENDING");
        } catch (RuntimeException e) {
            // 合并产物清理，保证失败重试不产生重复文件
            log.error("上传完成处理失败，清理合并产物：uploadId={}, key={}", uploadId, targetKey, e);
            storageService.delete(targetKey);
            throw e;
        }
    }

    private UploadSession requireSession(String uploadId) {
        if (uploadId == null || !UPLOAD_ID_PATTERN.matcher(uploadId).matches()) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        UploadSession session = sessionStore.get(uploadId);
        if (session == null) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        return session;
    }

    private int resolveEpisodeNo(UploadCompleteRequest request) {
        if (request.episodeNo() != null) {
            if (request.episodeNo() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "episodeNo 必须大于 0");
            }
            Long exists = episodeMapper.selectCount(Wrappers.<VideoEpisode>lambdaQuery()
                    .eq(VideoEpisode::getVideoId, request.videoId())
                    .eq(VideoEpisode::getEpisodeNo, request.episodeNo()));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该集数已存在");
            }
            return request.episodeNo();
        }
        VideoEpisode last = episodeMapper.selectOne(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getVideoId, request.videoId())
                .orderByDesc(VideoEpisode::getEpisodeNo)
                .last("LIMIT 1"));
        return last == null ? 1 : last.getEpisodeNo() + 1;
    }

    private String resolveTitle(String title, int episodeNo) {
        String value = (title == null || title.isBlank()) ? ("第" + episodeNo + "集") : title.trim();
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private long expectedChunkSize(UploadSession session, int index) {
        if (index < session.totalChunks() - 1) {
            return session.chunkSize();
        }
        return session.fileSize() - (long) index * session.chunkSize();
    }

    private String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null || !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件指纹格式不正确（需 64 位 SHA-256）");
        }
        return fingerprint.toLowerCase();
    }

    private void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件大小必须大于 0");
        }
        if (fileSize > maxFileSize) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件超过上限：" + maxFileSize + " 字节");
        }
    }
}
