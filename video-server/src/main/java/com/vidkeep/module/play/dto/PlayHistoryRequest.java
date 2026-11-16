package com.vidkeep.module.play.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 上报播放进度（文档 6.3.8）。
 */
public record PlayHistoryRequest(
        @NotBlank(message = "deviceId 不能为空")
        @Size(max = 64, message = "deviceId 长度不能超过 64") String deviceId,
        @NotNull(message = "videoId 不能为空") @Positive(message = "videoId 必须大于 0") Long videoId,
        @NotNull(message = "episodeId 不能为空") @Positive(message = "episodeId 必须大于 0") Long episodeId,
        @NotNull(message = "position 不能为空") @PositiveOrZero(message = "position 不能为负") Integer position,
        @PositiveOrZero(message = "duration 不能为负") Integer duration
) {
}
