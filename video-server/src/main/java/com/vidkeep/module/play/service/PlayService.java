package com.vidkeep.module.play.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.module.play.dto.PlayHistoryItemResponse;
import com.vidkeep.module.play.dto.PlayHistoryRequest;
import com.vidkeep.module.play.dto.PlayInfoResponse;
import com.vidkeep.module.play.entity.PlayHistory;
import com.vidkeep.module.play.mapper.PlayHistoryMapper;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 播放服务：播放地址、进度上报、进度查询（文档 6.3.7 / 6.3.8 / 7.3.3）。
 *
 * <p>访客免登录，用浏览器生成的 deviceId 识别设备。</p>
 */
@Service
public class PlayService {

    private static final Logger log = LoggerFactory.getLogger(PlayService.class);
    private static final String STATUS_DONE = "DONE";
    private static final int HISTORY_LIMIT = 100;

    private final VideoMapper videoMapper;
    private final VideoEpisodeMapper episodeMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final StorageService storageService;

    public PlayService(VideoMapper videoMapper,
                       VideoEpisodeMapper episodeMapper,
                       PlayHistoryMapper playHistoryMapper,
                       StorageService storageService) {
        this.videoMapper = videoMapper;
        this.episodeMapper = episodeMapper;
        this.playHistoryMapper = playHistoryMapper;
        this.storageService = storageService;
    }

    /**
     * 获取播放地址：校验作品上线、剧集转码完成、HLS 产物存在，并带上上次播放位置。
     */
    public PlayInfoResponse getPlayInfo(Long videoId, Long episodeId, String deviceId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || !Integer.valueOf(1).equals(video.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在或已下线");
        }
        VideoEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null || !videoId.equals(episode.getVideoId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "剧集不存在");
        }
        if (!STATUS_DONE.equals(episode.getTranscodeStatus())) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "视频尚未转码完成，请稍后再试");
        }
        if (episode.getHlsMasterUrl() == null || !storageService.exists(episode.getHlsMasterUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "播放文件不存在");
        }

        String url = "/hls/" + episodeId + "/master.m3u8";
        int duration = episode.getDuration() == null ? 0 : episode.getDuration();
        int lastPosition = resolveLastPosition(deviceId, episodeId, duration);
        return new PlayInfoResponse("HLS", url, video.getCoverUrl(), episode.getDuration(), lastPosition);
    }

    /**
     * 上报播放进度：按 (deviceId, episodeId) 唯一键做 upsert。
     */
    public void reportHistory(PlayHistoryRequest request) {
        VideoEpisode episode = episodeMapper.selectById(request.episodeId());
        if (episode == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "剧集不存在");
        }
        if (!request.videoId().equals(episode.getVideoId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "videoId 与剧集不匹配");
        }

        int position = Math.max(0, request.position());
        Integer duration = request.duration();
        if (duration != null && duration > 0) {
            // 进度不能超过时长
            position = Math.min(position, duration);
        }

        PlayHistory existing = playHistoryMapper.selectOne(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getDeviceId, request.deviceId())
                .eq(PlayHistory::getEpisodeId, request.episodeId())
                .last("LIMIT 1"));
        if (existing == null) {
            PlayHistory history = new PlayHistory();
            history.setDeviceId(request.deviceId());
            history.setVideoId(request.videoId());
            history.setEpisodeId(request.episodeId());
            history.setPosition(position);
            history.setDuration(duration);
            playHistoryMapper.insert(history);
        } else {
            existing.setPosition(position);
            existing.setDuration(duration);
            playHistoryMapper.updateById(existing);
        }
    }

    /**
     * 查询播放进度：按设备（可按作品过滤）。
     */
    public List<PlayHistoryItemResponse> queryHistory(String deviceId, Long videoId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "deviceId 不能为空");
        }
        List<PlayHistory> histories = playHistoryMapper.selectList(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getDeviceId, deviceId)
                .eq(videoId != null, PlayHistory::getVideoId, videoId)
                .orderByDesc(PlayHistory::getUpdatedAt)
                .last("LIMIT " + HISTORY_LIMIT));
        return histories.stream()
                .map(history -> new PlayHistoryItemResponse(
                        history.getEpisodeId(), history.getPosition(), history.getDuration(), history.getUpdatedAt()))
                .toList();
    }

    /**
     * 上次播放位置：进度 ≥95% 视为看完，从头播放（文档 7.3.3）。
     */
    private int resolveLastPosition(String deviceId, Long episodeId, int duration) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0;
        }
        PlayHistory history = playHistoryMapper.selectOne(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getDeviceId, deviceId)
                .eq(PlayHistory::getEpisodeId, episodeId)
                .last("LIMIT 1"));
        if (history == null || history.getPosition() == null) {
            return 0;
        }
        if (duration > 0 && history.getPosition() >= duration * 0.95) {
            return 0;
        }
        return Math.max(0, history.getPosition());
    }
}
