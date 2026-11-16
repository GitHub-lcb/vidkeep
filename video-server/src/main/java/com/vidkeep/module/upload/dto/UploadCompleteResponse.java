package com.vidkeep.module.upload.dto;

/**
 * 完成上传响应（文档 6.3.5）：入库后剧集进入转码队列。
 */
public record UploadCompleteResponse(Long episodeId, String transcodeStatus) {
}
