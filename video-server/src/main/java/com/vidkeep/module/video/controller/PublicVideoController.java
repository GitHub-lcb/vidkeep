package com.vidkeep.module.video.controller;

import com.vidkeep.common.PageResult;
import com.vidkeep.common.Result;
import com.vidkeep.module.video.dto.PublicVideoDetailVO;
import com.vidkeep.module.video.dto.PublicVideoItemVO;
import com.vidkeep.module.video.service.PublicVideoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开视频接口（文档 6.2 播放模块）：免鉴权，只返回上线作品。
 */
@RestController
@RequestMapping("/api/public/videos")
public class PublicVideoController {

    private final PublicVideoService publicVideoService;

    public PublicVideoController(PublicVideoService publicVideoService) {
        this.publicVideoService = publicVideoService;
    }

    /**
     * 前台视频分页列表：GET /api/public/videos?page=1&size=20&keyword=&categoryId=。
     */
    @GetMapping
    public Result<PageResult<PublicVideoItemVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(publicVideoService.list(page, size, keyword, categoryId));
    }

    /**
     * 前台视频详情（含剧集）：GET /api/public/videos/{id}；非上线或不存在返回 3001。
     */
    @GetMapping("/{id}")
    public Result<PublicVideoDetailVO> detail(@PathVariable Long id) {
        return Result.ok(publicVideoService.detail(id));
    }
}
