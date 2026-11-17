package com.vidkeep.module.site.dto;

/**
 * 站点口令状态（GET /api/public/site/status）。
 */
public record SiteStatusResponse(boolean enabled) {
}
