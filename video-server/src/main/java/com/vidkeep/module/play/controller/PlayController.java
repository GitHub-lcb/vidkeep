package com.vidkeep.module.play.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.play.dto.PlayHistoryItemResponse;
import com.vidkeep.module.play.dto.PlayHistoryRequest;
import com.vidkeep.module.play.dto.PlayInfoResponse;
import com.vidkeep.module.play.service.PlayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 播放接口（文档 6.2 播放模块，公开免登录）。
 */
@RestController
@RequestMapping("/api/public")
public class PlayController {

    private final PlayService playService;

    public PlayController(PlayService playService) {
        this.playService = playService;
    }

    /**
     * 获取播放地址（文档 6.3.7）。
     */
    @GetMapping("/videos/{videoId}/episodes/{episodeId}/play")
    public Result<PlayInfoResponse> play(@PathVariable Long videoId,
                                         @PathVariable Long episodeId,
                                         @RequestParam(required = false) String deviceId) {
        return Result.ok(playService.getPlayInfo(videoId, episodeId, deviceId));
    }

    /**
     * 上报播放进度（文档 6.3.8）。
     */
    @PostMapping("/play/history")
    public Result<Void> reportHistory(@Valid @RequestBody PlayHistoryRequest request) {
        playService.reportHistory(request);
        return Result.ok();
    }

    /**
     * 查询播放进度（按 deviceId，可按 videoId 过滤）。
     */
    @GetMapping("/play/history")
    public Result<List<PlayHistoryItemResponse>> queryHistory(
            @RequestParam String deviceId,
            @RequestParam(required = false) Long videoId) {
        return Result.ok(playService.queryHistory(deviceId, videoId));
    }
}
