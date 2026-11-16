package com.vidkeep.module.upload.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.upload.dto.UploadCheckRequest;
import com.vidkeep.module.upload.dto.UploadCheckResponse;
import com.vidkeep.module.upload.dto.UploadChunkResponse;
import com.vidkeep.module.upload.dto.UploadCompleteRequest;
import com.vidkeep.module.upload.dto.UploadCompleteResponse;
import com.vidkeep.module.upload.dto.UploadInitRequest;
import com.vidkeep.module.upload.dto.UploadInitResponse;
import com.vidkeep.module.upload.dto.UploadStatusResponse;
import com.vidkeep.module.upload.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 分片上传接口（文档 6.2 / 6.3.2–6.3.5），全部需要 ADMIN 权限。
 */
@RestController
@RequestMapping("/api/admin/upload")
@PreAuthorize("hasRole('ADMIN')")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 秒传检查 + 续传会话查询。
     */
    @PostMapping("/check")
    public Result<UploadCheckResponse> check(@Valid @RequestBody UploadCheckRequest request) {
        return Result.ok(uploadService.check(request));
    }

    /**
     * 初始化上传会话。
     */
    @PostMapping("/init")
    public Result<UploadInitResponse> init(@Valid @RequestBody UploadInitRequest request) {
        return Result.ok(uploadService.init(request));
    }

    /**
     * 上传分片：Body 为原始二进制，Content-Type 必须为 application/octet-stream。
     */
    @PutMapping(value = "/{uploadId}/chunk/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Result<UploadChunkResponse> uploadChunk(@PathVariable String uploadId,
                                                   @PathVariable int index,
                                                   HttpServletRequest request) throws IOException {
        return Result.ok(uploadService.uploadChunk(uploadId, index, request.getInputStream()));
    }

    /**
     * 查询已上传分片（断点续传）。
     */
    @GetMapping("/{uploadId}/status")
    public Result<UploadStatusResponse> status(@PathVariable String uploadId) {
        return Result.ok(uploadService.status(uploadId));
    }

    /**
     * 完成上传：合并分片、入库并投递转码任务。
     */
    @PostMapping("/{uploadId}/complete")
    public Result<UploadCompleteResponse> complete(@PathVariable String uploadId,
                                                   @Valid @RequestBody UploadCompleteRequest request) {
        return Result.ok(uploadService.complete(uploadId, request));
    }
}
