package com.vidkeep.infra.ffmpeg;

/**
 * 输出档位（文档 7.2.4）。
 */
public enum TranscodeProfile {

    P720(720, "23", "128k", 1_800_000, "1280x720"),
    P480(480, "24", "96k", 900_000, "854x480");

    private final int height;
    private final String crf;
    private final String audioBitRate;
    private final int bandwidth;
    private final String resolution;

    TranscodeProfile(int height, String crf, String audioBitRate, int bandwidth, String resolution) {
        this.height = height;
        this.crf = crf;
        this.audioBitRate = audioBitRate;
        this.bandwidth = bandwidth;
        this.resolution = resolution;
    }

    public int getHeight() {
        return height;
    }

    public String getCrf() {
        return crf;
    }

    public String getAudioBitRate() {
        return audioBitRate;
    }

    /** master.m3u8 的 BANDWIDTH 声明值（bps） */
    public int getBandwidth() {
        return bandwidth;
    }

    public String getResolution() {
        return resolution;
    }

    /** 输出子目录名，如 720p */
    public String getDirectoryName() {
        return height + "p";
    }
}
