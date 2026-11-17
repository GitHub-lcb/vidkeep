package com.vidkeep.module.site.dto;

/**
 * 校验站点访问口令响应（POST /api/public/site/verify）。
 */
public record SiteVerifyResponse(boolean verified) {
}
