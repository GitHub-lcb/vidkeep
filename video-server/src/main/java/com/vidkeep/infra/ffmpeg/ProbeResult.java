package com.vidkeep.infra.ffmpeg;

/**
 * ffprobe 探测结果（文档 7.2.3，只取转码决策需要的字段）。
 *
 * @param codecName       视频编码名，如 h264 / hevc
 * @param formatName      容器格式，如 mov,mp4,m4a,3gp,3g2,mj2 / matroska,webm
 * @param bitRate         码率（bps，优先取容器码率）
 * @param width           宽
 * @param height          高
 * @param durationSeconds 时长（秒）
 */
public record ProbeResult(
        String codecName,
        String formatName,
        long bitRate,
        int width,
        int height,
        double durationSeconds
) {

    public boolean isH264() {
        return "h264".equalsIgnoreCase(codecName);
    }

    public boolean isMp4Container() {
        return formatName != null && formatName.toLowerCase().contains("mp4");
    }
}
