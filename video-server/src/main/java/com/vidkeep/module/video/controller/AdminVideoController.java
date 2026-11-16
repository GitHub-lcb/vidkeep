package com.vidkeep.module.video.controller;

import com.vidkeep.common.PageResult;
import com.vidkeep.common.Result;
import com.vidkeep.module.video.dto.VideoAdminDetailVO;
import com.vidkeep.module.video.dto.VideoAdminItemVO;
import com.vidkeep.module.video.dto.VideoSaveRequest;
import com.vidkeep.module.video.dto.VideoStatusRequest;
import com.vidkeep.module.video.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频管理接口（文档 6.2 视频管理）：仅 ADMIN 可访问。
 *
 * <p>含作品 CRUD、上下架与单集删除；上传/转码相关接口分别由 upload、transcode 模块提供。</p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVideoController {

    private final VideoService videoService;

    public AdminVideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * 视频分页列表：GET /api/admin/videos?page=1&size=20&title=&status=&categoryId=。
     */
    @GetMapping("/videos")
    public Result<PageResult<VideoAdminItemVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(videoService.list(page, size, title, status, categoryId));
    }

    /**
     * 视频详情（含剧集）：GET /api/admin/videos/{id}；不存在返回 3001。
     */
    @GetMapping("/videos/{id}")
    public Result<VideoAdminDetailVO> detail(@PathVariable Long id) {
        return Result.ok(videoService.detail(id));
    }

    /**
     * 新建作品：POST /api/admin/videos，返回新视频 ID（先建壳，再上传文件）。
     */
    @PostMapping("/videos")
    public Result<Long> create(@Valid @RequestBody VideoSaveRequest request) {
        return Result.ok(videoService.create(request));
    }

    /**
     * 编辑作品：PUT /api/admin/videos/{id}。
     */
    @PutMapping("/videos/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VideoSaveRequest request) {
        videoService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除作品：DELETE /api/admin/videos/{id}，级联删除剧集、文件、转码任务与播放进度。
     */
    @DeleteMapping("/videos/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        videoService.delete(id);
        return Result.ok();
    }

    /**
     * 上下架：PUT /api/admin/videos/{id}/status，status 只允许 0/1。
     */
    @PutMapping("/videos/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody VideoStatusRequest request) {
        videoService.updateStatus(id, request.status());
        return Result.ok();
    }

    /**
     * 删除单集：DELETE /api/admin/episodes/{id}，级联删除原片、HLS、转码任务与播放进度。
     */
    @DeleteMapping("/episodes/{id}")
    public Result<Void> deleteEpisode(@PathVariable Long id) {
        videoService.deleteEpisode(id);
        return Result.ok();
    }
}
