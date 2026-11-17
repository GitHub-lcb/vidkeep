package com.vidkeep.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 站点访问口令过滤器（P1 F-18）。
 *
 * <p>口令开启时，未携带有效口令 cookie 访问公开接口/媒体资源会被拦截：
 * API 返回 HTTP 200 + code 2004（前端弹口令框）；/hls、/covers 返回 403（媒体请求）。
 * 已登录管理员不受限制。</p>
 */
@Component
public class SiteAccessFilter extends OncePerRequestFilter {

    private static final String SITE_STATUS_PATH = "/api/public/site/status";
    private static final String SITE_VERIFY_PATH = "/api/public/site/verify";

    private final SiteAccessService siteAccessService;
    private final ObjectMapper objectMapper;

    public SiteAccessFilter(SiteAccessService siteAccessService, ObjectMapper objectMapper) {
        this.siteAccessService = siteAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!siteAccessService.isEnabled()
                || isExemptPath(request)
                || isAuthenticatedAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (siteAccessService.verifyToken(readCookie(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(request, response);
    }

    /**
     * 只拦截公开 API、HLS 与封面；后台接口由 JWT 自行保护。
     */
    private boolean requiresGate(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/public/")
                || path.startsWith("/hls/")
                || path.startsWith("/covers/");
    }

    private boolean isExemptPath(HttpServletRequest request) {
        if (!requiresGate(request)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return SITE_STATUS_PATH.equals(path) || SITE_VERIFY_PATH.equals(path);
    }

    private boolean isAuthenticatedAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (siteAccessService.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.SITE_PASSWORD_REQUIRED));
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.SITE_PASSWORD_REQUIRED));
        }
    }
}
