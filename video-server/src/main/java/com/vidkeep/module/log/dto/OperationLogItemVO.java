package com.vidkeep.module.log.dto;

import java.time.LocalDateTime;

/**
 * 管理员操作日志项（GET /api/admin/operation-logs）。
 */
public record OperationLogItemVO(
        Long id,
        Long userId,
        String username,
        String module,
        String action,
        String method,
        String path,
        String detail,
        String ip,
        Integer durationMs,
        LocalDateTime createdAt
) {
}
