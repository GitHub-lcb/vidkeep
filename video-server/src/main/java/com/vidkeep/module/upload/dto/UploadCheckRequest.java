package com.vidkeep.module.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 秒传检查 / 续传会话查询请求（文档 6.3.2）。
 */
public record UploadCheckRequest(
        @NotBlank(message = "文件指纹不能为空") String fingerprint,
        @NotBlank(message = "文件名不能为空") String fileName,
        @Positive(message = "文件大小必须大于 0") long fileSize
) {
}
