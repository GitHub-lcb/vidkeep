package com.vidkeep.module.video.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台视频详情（GET /api/public/videos/{id}，对应前端 PublicVideoDetail）。
 *
 * <p>仅上线（status=1）的视频可访问；未上线或不存在统一返回 3001。</p>
 */
public record PublicVideoDetailVO(
        Long id,
        String title,
        String description,
        String coverUrl,
        Long categoryId,
        String categoryName,
        Long viewCount,
        LocalDateTime createdAt,
        List<PublicEpisodeVO> episodes
) {
}
