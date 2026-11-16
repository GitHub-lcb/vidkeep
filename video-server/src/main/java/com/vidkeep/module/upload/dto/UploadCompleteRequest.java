package com.vidkeep.module.upload.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 完成上传请求（文档 6.3.5）。
 *
 * @param videoId   所属作品 ID
 * @param episodeNo 第几集；不传则自动取当前作品最大集数 + 1
 * @param title     剧集标题；不传则默认「第 N 集」
 * @param coverMode 封面模式：AUTO 转码后自动截帧 / NONE 不自动生成
 */
public record UploadCompleteRequest(
        @NotNull(message = "videoId 不能为空") @Positive(message = "videoId 必须大于 0") Long videoId,
        Integer episodeNo,
        String title,
        String coverMode
) {
    public String coverModeOrDefault() {
        return coverMode == null || coverMode.isBlank() ? "AUTO" : coverMode.toUpperCase();
    }
}
