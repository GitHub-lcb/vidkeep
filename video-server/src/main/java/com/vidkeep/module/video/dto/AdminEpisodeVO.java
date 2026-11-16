package com.vidkeep.module.video.dto;

import java.time.LocalDateTime;

/**
 * 后台剧集详情（GET /api/admin/videos/{id} 的 episodes[]，对应前端 EpisodeItem）。
 *
 * @param id              剧集 ID
 * @param videoId         所属视频 ID
 * @param episodeNo       第几集
 * @param title           剧集标题
 * @param playType        播放类型（预留 HLS_LOCAL）
 * @param duration        时长（秒）
 * @param fileSize        原片大小（字节）
 * @param width           宽度
 * @param height          高度
 * @param bitRate         码率（bps）
 * @param transcodeStatus 转码状态 NONE/PENDING/PROCESSING/DONE/FAILED
 * @param transcodeMode   转码模式 REMUX/TRANSCODE
 * @param hlsMasterUrl    HLS 主播放列表相对路径
 * @param createdAt       创建时间
 */
public record AdminEpisodeVO(
        Long id,
        Long videoId,
        Integer episodeNo,
        String title,
        String playType,
        Integer duration,
        Long fileSize,
        Integer width,
        Integer height,
        Integer bitRate,
        String transcodeStatus,
        String transcodeMode,
        String hlsMasterUrl,
        LocalDateTime createdAt
) {
}
