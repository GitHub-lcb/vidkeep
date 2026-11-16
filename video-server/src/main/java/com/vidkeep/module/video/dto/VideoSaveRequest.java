package com.vidkeep.module.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建/编辑视频作品请求（POST/PUT /api/admin/videos，对应前端 VideoSaveRequest）。
 *
 * @param title       标题，必填 1..255
 * @param description 简介，可空
 * @param coverUrl    封面地址，可空
 * @param categoryId  分类 ID，可空；传了则必须存在
 */
public record VideoSaveRequest(
        @NotBlank(message = "标题不能为空")
        @Size(min = 1, max = 255, message = "标题长度需为 1..255")
        String title,

        String description,

        String coverUrl,

        Long categoryId
) {
}
