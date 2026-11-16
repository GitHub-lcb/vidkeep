package com.vidkeep.module.upload.dto;

/**
 * 上传分片响应（文档 6.3.4）。
 */
public record UploadChunkResponse(int index, boolean received) {
}
