package com.vidkeep.module.video.dto;

import java.time.LocalDateTime;

/**
 * 前台视频列表项（GET /api/public/videos，字段与前端 src/types/video.ts 的 PublicVideoItem 对齐）。
 *
 * @param id           视频 ID
 * @param title        标题
 * @param coverUrl     封面地址
 * @param categoryId   分类 ID
 * @param categoryName 分类名称（批量查询）
 * @param viewCount    播放量
 * @param episodeCount 剧集数（批量统计）
 * @param duration     时长（取第一集 duration，无剧集则为 null）
 * @param createdAt    创建时间
 */
public record PublicVideoItemVO(
        Long id,
        String title,
        String coverUrl,
        Long categoryId,
        String categoryName,
        Long viewCount,
        Integer episodeCount,
        Integer duration,
        LocalDateTime createdAt
) {
}
