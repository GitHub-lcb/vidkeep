package com.vidkeep.module.log.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidkeep.common.PageResult;
import com.vidkeep.module.log.dto.OperationLogItemVO;
import com.vidkeep.module.log.entity.AdminOperationLog;
import com.vidkeep.module.log.mapper.AdminOperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理员操作日志服务（P1 F-19）。
 */
@Service
public class OperationLogService {

    private static final long MAX_PAGE_SIZE = 200L;

    private final AdminOperationLogMapper operationLogMapper;

    public OperationLogService(AdminOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 写入审计日志。
     */
    public void save(AdminOperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    /**
     * 分页查询审计日志。
     */
    public PageResult<OperationLogItemVO> page(long page, long size, String username, String module) {
        long current = Math.max(page, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<AdminOperationLog> result = operationLogMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<AdminOperationLog>lambdaQuery()
                        .like(StringUtils.hasText(username), AdminOperationLog::getUsername,
                                username == null ? null : username.trim())
                        .eq(StringUtils.hasText(module), AdminOperationLog::getModule, module)
                        .orderByDesc(AdminOperationLog::getId));
        List<OperationLogItemVO> records = result.getRecords().stream()
                .map(item -> new OperationLogItemVO(
                        item.getId(), item.getUserId(), item.getUsername(), item.getModule(),
                        item.getAction(), item.getMethod(), item.getPath(), item.getDetail(),
                        item.getIp(), item.getDurationMs(), item.getCreatedAt()))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }
}
