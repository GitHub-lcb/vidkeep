package com.vidkeep.module.log.controller;

import com.vidkeep.common.PageResult;
import com.vidkeep.common.Result;
import com.vidkeep.module.log.dto.OperationLogItemVO;
import com.vidkeep.module.log.service.OperationLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员操作日志查询（P1 F-19）。
 */
@RestController
@RequestMapping("/api/admin/operation-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    public AdminOperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志，可按用户名/模块筛选。
     */
    @GetMapping
    public Result<PageResult<OperationLogItemVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module) {
        return Result.ok(operationLogService.page(page, size, username, module));
    }
}
