package com.vidkeep.module.stats.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.stats.dto.AdminStatsResponse;
import com.vidkeep.module.stats.service.StatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台统计接口（P1 F-15）。
 */
@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final StatsService statsService;

    public AdminStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public Result<AdminStatsResponse> stats() {
        return Result.ok(statsService.stats());
    }
}
