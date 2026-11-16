package com.vidkeep.infra.ffmpeg;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 转码决策（文档 7.2.2，核心逻辑，需可单测）。
 *
 * <p>规则：H.264 + MP4 + 码率 ≤ 3Mbps → 只 REMUX；否则重编码，
 * 源分辨率 ≥720P 输出 720P + 480P，否则只输出 480P。</p>
 */
@Component
public class TranscodeDecider {

    /** REMUX 的码率上限：3 Mbps */
    private static final long REMUX_MAX_BIT_RATE = 3_000_000L;

    public TranscodeDecision decide(ProbeResult probe) {
        boolean remuxable = probe.isH264() && probe.isMp4Container() && probe.bitRate() <= REMUX_MAX_BIT_RATE;
        if (remuxable) {
            return new TranscodeDecision(TranscodeDecision.TranscodeMode.REMUX, List.of(TranscodeProfile.P720));
        }

        List<TranscodeProfile> profiles = new ArrayList<>();
        if (probe.height() >= 720) {
            profiles.add(TranscodeProfile.P720);
        }
        profiles.add(TranscodeProfile.P480);
        return new TranscodeDecision(TranscodeDecision.TranscodeMode.TRANSCODE, profiles);
    }
}
