package com.vidkeep.module.upload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 秒传检查 / 续传会话查询响应（文档 6.3.2）。
 *
 * @param exists         是否秒传命中
 * @param episodeId      命中时返回已有剧集 ID
 * @param uploadId       未命中时返回可续传会话 ID
 * @param chunkSize      分片大小
 * @param totalChunks    分片总数
 * @param uploadedChunks 已上传分片序号
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadCheckResponse(
        boolean exists,
        Long episodeId,
        String uploadId,
        Integer chunkSize,
        Integer totalChunks,
        List<Integer> uploadedChunks
) {
    public static UploadCheckResponse hit(Long episodeId) {
        return new UploadCheckResponse(true, episodeId, null, null, null, null);
    }

    public static UploadCheckResponse miss(String uploadId, int chunkSize, int totalChunks, List<Integer> uploadedChunks) {
        return new UploadCheckResponse(false, null, uploadId, chunkSize, totalChunks, uploadedChunks);
    }

    public static UploadCheckResponse notStarted() {
        return new UploadCheckResponse(false, null, null, null, null, List.of());
    }
}
