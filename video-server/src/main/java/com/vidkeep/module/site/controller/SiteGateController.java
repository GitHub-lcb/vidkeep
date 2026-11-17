package com.vidkeep.module.site.controller;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.Result;
import com.vidkeep.module.site.dto.SiteStatusResponse;
import com.vidkeep.module.site.dto.SiteVerifyRequest;
import com.vidkeep.module.site.dto.SiteVerifyResponse;
import com.vidkeep.security.SiteAccessService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 站点访问口令接口（P1 F-18）。
 */
@RestController
@RequestMapping("/api/public/site")
public class SiteGateController {

    private final SiteAccessService siteAccessService;

    public SiteGateController(SiteAccessService siteAccessService) {
        this.siteAccessService = siteAccessService;
    }

    /**
     * 查询站点是否开启口令（前端用于决定是否弹口令框）。
     */
    @GetMapping("/status")
    public Result<SiteStatusResponse> status() {
        return Result.ok(new SiteStatusResponse(siteAccessService.isEnabled()));
    }

    /**
     * 校验口令；通过后写入 HttpOnly cookie（有效期见配置，默认 30 天）。
     */
    @PostMapping("/verify")
    public Result<SiteVerifyResponse> verify(@Valid @RequestBody SiteVerifyRequest request,
                                             HttpServletResponse response) {
        if (!siteAccessService.isEnabled()) {
            return Result.ok(new SiteVerifyResponse(true));
        }
        if (!siteAccessService.passwordMatches(request.password())) {
            throw new BizException(ErrorCode.SITE_PASSWORD_INCORRECT);
        }
        String token = siteAccessService.issueToken();
        ResponseCookie cookie = ResponseCookie.from(siteAccessService.getCookieName(), token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(siteAccessService.getTokenExpireSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.ok(new SiteVerifyResponse(true));
    }
}
