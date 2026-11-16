package com.vidkeep.module.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 播放单元（对应 video_episode 表）：一个作品可以挂多个视频文件（多集/多 P）。
 */
@TableName("video_episode")
public class VideoEpisode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    /** 第几集，单文件作品固定为 1 */
    private Integer episodeNo;

    private String title;

    /** 预留：HLS_LOCAL 本地转码 */
    private String playType;

    /** 时长（秒） */
    private Integer duration;

    /** 原片相对路径：uploads/{yyyy}/{MM}/{uuid}.mp4 */
    private String storageKey;

    private Long fileSize;

    /** 前端抽样指纹（SHA-256，64 位），秒传用 */
    private String fileFingerprint;

    /** 服务端合并后的完整 MD5 */
    private String fileMd5;

    private Integer width;

    private Integer height;

    /** 码率（bps） */
    private Integer bitRate;

    /** NONE / PENDING / PROCESSING / DONE / FAILED */
    private String transcodeStatus;

    /** REMUX / TRANSCODE */
    private String transcodeMode;

    /** HLS 主播放列表相对路径：hls/{episodeId}/master.m3u8 */
    private String hlsMasterUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Integer getEpisodeNo() {
        return episodeNo;
    }

    public void setEpisodeNo(Integer episodeNo) {
        this.episodeNo = episodeNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPlayType() {
        return playType;
    }

    public void setPlayType(String playType) {
        this.playType = playType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileFingerprint() {
        return fileFingerprint;
    }

    public void setFileFingerprint(String fileFingerprint) {
        this.fileFingerprint = fileFingerprint;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getBitRate() {
        return bitRate;
    }

    public void setBitRate(Integer bitRate) {
        this.bitRate = bitRate;
    }

    public String getTranscodeStatus() {
        return transcodeStatus;
    }

    public void setTranscodeStatus(String transcodeStatus) {
        this.transcodeStatus = transcodeStatus;
    }

    public String getTranscodeMode() {
        return transcodeMode;
    }

    public void setTranscodeMode(String transcodeMode) {
        this.transcodeMode = transcodeMode;
    }

    public String getHlsMasterUrl() {
        return hlsMasterUrl;
    }

    public void setHlsMasterUrl(String hlsMasterUrl) {
        this.hlsMasterUrl = hlsMasterUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
