package com.vidkeep.module.video.dto;

import java.time.LocalDateTime;

/**
 * 后台视频列表项（GET /api/admin/videos，字段与前端 src/types/video.ts 的 VideoAdminItem 对齐）。
 *
 * @param id            视频 ID
 * @param title         标题
 * @param description   简介
 * @param coverUrl      封面地址
 * @param categoryId    分类 ID
 * @param categoryName  分类名称（批量查询后填充，避免 N+1）
 * @param viewCount     播放量
 * @param status        1 上线，0 下线
 * @param episodeCount  剧集数（批量统计后填充）
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record VideoAdminItemVO(
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
        LocalDateTime updatedAt
) {
}
