package com.vidkeep.infra.ffmpeg;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转码决策单元测试（文档 7.2.2）。
 */
class TranscodeDeciderTest {

    private final TranscodeDecider decider = new TranscodeDecider();

    @Test
    void shouldRemuxWhenH264Mp4AndBitrateWithinLimit() {
        ProbeResult probe = new ProbeResult("h264", "mov,mp4,m4a,3gp,3g2,mj2", 2_270_000L, 1920, 1080, 10800);

        TranscodeDecision decision = decider.decide(probe);

        assertEquals(TranscodeDecision.TranscodeMode.REMUX, decision.mode());
        assertEquals(List.of(TranscodeProfile.P720), decision.profiles());
    }

    @Test
    void shouldTranscodeWhenBitrateTooHigh() {
        ProbeResult probe = new ProbeResult("h264", "mov,mp4,m4a,3gp,3g2,mj2", 8_000_000L, 1920, 1080, 3600);

        TranscodeDecision decision = decider.decide(probe);

        assertEquals(TranscodeDecision.TranscodeMode.TRANSCODE, decision.mode());
        assertEquals(List.of(TranscodeProfile.P720, TranscodeProfile.P480), decision.profiles());
    }

    @Test
    void shouldTranscodeWhenCodecNotH264() {
        ProbeResult probe = new ProbeResult("hevc", "matroska,webm", 4_000_000L, 3840, 2160, 600);

        TranscodeDecision decision = decider.decide(probe);

        assertEquals(TranscodeDecision.TranscodeMode.TRANSCODE, decision.mode());
        assertTrue(decision.profiles().contains(TranscodeProfile.P720));
    }

    @Test
    void shouldOnlyOutput480WhenSourceBelow720() {
        ProbeResult probe = new ProbeResult("mpeg4", "avi", 1_500_000L, 640, 480, 300);

        TranscodeDecision decision = decider.decide(probe);

        assertEquals(TranscodeDecision.TranscodeMode.TRANSCODE, decision.mode());
        assertEquals(List.of(TranscodeProfile.P480), decision.profiles());
    }
}
