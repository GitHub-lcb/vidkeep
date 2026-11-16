package com.vidkeep.module.transcode.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.transcode.dto.TranscodeProgressResponse;
import com.vidkeep.module.transcode.service.TranscodeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 转码管理接口（文档 6.2 转码模块），需要 ADMIN 权限。
 */
@RestController
@RequestMapping("/api/admin/episodes")
@PreAuthorize("hasRole('ADMIN')")
public class TranscodeController {

    private final TranscodeService transcodeService;

    public TranscodeController(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    /**
     * 查询转码状态与进度。
     */
    @GetMapping("/{episodeId}/transcode")
    public Result<TranscodeProgressResponse> query(@PathVariable Long episodeId) {
        return Result.ok(transcodeService.queryProgress(episodeId));
    }

    /**
     * 重试转码（失败后最多重试 3 次）。
     */
    @PostMapping("/{episodeId}/transcode/retry")
    public Result<Void> retry(@PathVariable Long episodeId) {
        transcodeService.retry(episodeId);
        return Result.ok();
    }

    /**
     * 取消转码。
     */
    @PostMapping("/{episodeId}/transcode/cancel")
    public Result<Void> cancel(@PathVariable Long episodeId) {
        transcodeService.cancel(episodeId);
        return Result.ok();
    }
}
