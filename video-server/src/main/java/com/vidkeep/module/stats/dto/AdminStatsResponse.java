package com.vidkeep.module.stats.dto;

/**
 * 后台统计（P1 F-15，GET /api/admin/stats）。
 */
public record AdminStatsResponse(
        long videoCount,
        long categoryCount,
        long episodeCount,
        long transcodingCount,
        long totalViewCount,
        long totalWatchSeconds
) {
}
