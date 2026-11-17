package com.vidkeep.module.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 校验站点访问口令请求（POST /api/public/site/verify）。
 */
public record SiteVerifyRequest(
        @NotBlank(message = "访问口令不能为空")
        @Size(max = 128, message = "访问口令过长") String password
) {
}
