package com.vidkeep.module.upload.service;

/**
 * 上传会话（存 Redis，TTL 24 小时，文档 7.1.3）。
 *
 * @param uploadId   上传会话 ID
 * @param fileName   原始文件名
 * @param fileSize   文件大小（字节）
 * @param fingerprint 前端抽样指纹（SHA-256）
 * @param chunkSize  分片大小（字节）
 * @param totalChunks 分片总数
 * @param createdAt  创建时间（epoch millis）
 */
public record UploadSession(
        String uploadId,
        String fileName,
        long fileSize,
        String fingerprint,
        int chunkSize,
        int totalChunks,
        long createdAt
) {
}
