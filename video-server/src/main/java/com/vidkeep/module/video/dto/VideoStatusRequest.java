package com.vidkeep.module.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 视频上下架请求（PUT /api/admin/videos/{id}/status，对应前端 VideoStatusRequest）。
 *
 * @param status 1 上线，0 下线；只允许 0/1
 */
public record VideoStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只允许 0 或 1")
        @Max(value = 1, message = "状态只允许 0 或 1")
        Integer status
) {
}
