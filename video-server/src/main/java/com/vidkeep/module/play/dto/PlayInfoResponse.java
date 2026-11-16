package com.vidkeep.module.play.dto;

/**
 * 播放地址（文档 6.3.7）。
 */
public record PlayInfoResponse(
        String type,
        String url,
        String poster,
        Integer duration,
        int lastPosition
) {
}
