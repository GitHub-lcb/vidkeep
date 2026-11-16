package com.vidkeep.module.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 初始化上传请求（文档 6.3.3）。
 */
public record UploadInitRequest(
        @NotBlank(message = "文件名不能为空") String fileName,
        @Positive(message = "文件大小必须大于 0") long fileSize,
        @NotBlank(message = "文件指纹不能为空") String fingerprint
) {
}
