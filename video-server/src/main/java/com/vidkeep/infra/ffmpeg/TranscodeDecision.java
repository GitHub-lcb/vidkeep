package com.vidkeep.infra.ffmpeg;

import java.util.List;

/**
 * 转码决策结果（文档 7.2.2）。
 *
 * @param mode     转码方式
 * @param profiles 输出档位（REMUX 时只有一档）
 */
public record TranscodeDecision(TranscodeMode mode, List<TranscodeProfile> profiles) {

    public enum TranscodeMode {
        /** 只转封装，不重编码：最快、无损 */
        REMUX,
        /** 重编码为 HLS */
        TRANSCODE
    }
}
