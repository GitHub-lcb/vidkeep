package com.vidkeep.module.video.dto;

/**
 * 前台剧集项（GET /api/public/videos/{id} 的 episodes[]，对应前端 PublicEpisodeItem）。
 *
 * <p>只暴露播放所需字段，不透出存储路径等内部信息。</p>
 */
public record PublicEpisodeVO(
        Long id,
        Integer episodeNo,
        String title,
        Integer duration,
        String transcodeStatus
) {
}
