package com.vidkeep.module.upload.dto;

/**
 * 初始化上传响应（文档 6.3.3）。
 */
public record UploadInitResponse(String uploadId, int chunkSize, int totalChunks) {
}
