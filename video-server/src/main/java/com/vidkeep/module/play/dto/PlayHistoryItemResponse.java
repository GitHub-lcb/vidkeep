package com.vidkeep.module.play.dto;

import java.time.LocalDateTime;

/**
 * 播放进度项（GET /api/public/play/history）。
 */
public record PlayHistoryItemResponse(
        Long episodeId,
        Integer position,
        Integer duration,
        LocalDateTime updatedAt
) {
}
