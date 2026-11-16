package com.vidkeep.module.upload.dto;

import java.util.List;

/**
 * 已上传分片查询响应（断点续传）。
 */
public record UploadStatusResponse(String uploadId, int chunkSize, int totalChunks, List<Integer> uploadedChunks) {
}
