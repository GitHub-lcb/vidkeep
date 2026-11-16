package com.vidkeep.module.video.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台视频详情（GET /api/admin/videos/{id}，对应前端 VideoAdminDetail）。
 *
 * <p>字段与列表项一致，额外携带按 episodeNo 升序的剧集列表。</p>
 */
public record VideoAdminDetailVO(
        Long id,
        String title,
        String description,
        String coverUrl,
        Long categoryId,
        String categoryName,
        Long viewCount,
        Integer status,
        Integer episodeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AdminEpisodeVO> episodes
) {
}
