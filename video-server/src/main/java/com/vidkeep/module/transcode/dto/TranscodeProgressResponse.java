package com.vidkeep.module.transcode.dto;

/**
 * 转码进度（文档 6.3.6）。
 *
 * @param episodeId 剧集 ID
 * @param status    NONE / PENDING / PROCESSING / DONE / FAILED
 * @param progress  0-100
 * @param mode      REMUX / TRANSCODE（未开始为 null）
 * @param errorMsg  失败原因
 */
public record TranscodeProgressResponse(
        Long episodeId,
        String status,
        int progress,
        String mode,
        String errorMsg
) {
}
