package com.vidkeep.common.log;

import com.vidkeep.module.log.entity.AdminOperationLog;
import com.vidkeep.module.log.service.OperationLogService;
import com.vidkeep.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 管理员操作日志切面（P1 F-19）。
 *
 * <p>自动审计 {@code /api/admin/**} 下的写操作（POST/PUT/DELETE/PATCH）。
 * 分片上传（chunk）与秒传检查（check）请求量大且无审计价值，跳过。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("execution(public * com.vidkeep.module..controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        boolean auditable = request != null && isAuditable(request);
        long startedAt = System.nanoTime();
        if (!auditable) {
            return joinPoint.proceed();
        }
        try {
            Object result = joinPoint.proceed();
            record(request, startedAt, null);
            return result;
        } catch (Throwable throwable) {
            record(request, startedAt, throwable.getMessage());
            throw throwable;
        }
    }

    private boolean isAuditable(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) {
            return false;
        }
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return false;
        }
        // 分片上传与秒传检查请求量大，不记审计
        return !path.contains("/chunk/") && !path.endsWith("/upload/check");
    }

    private void record(HttpServletRequest request, long startedAt, String errorMessage) {
        try {
            AdminOperationLog operationLog = new AdminOperationLog();
            operationLog.setModule(resolveModule(request.getRequestURI()));
            operationLog.setAction(resolveAction(request.getMethod(), request.getRequestURI()));
            operationLog.setMethod(request.getMethod());
            operationLog.setPath(truncate(request.getRequestURI(), 255));
            operationLog.setDetail(buildDetail(request, errorMessage));
            operationLog.setIp(resolveIp(request));
            operationLog.setDurationMs((int) Math.min(Integer.MAX_VALUE,
                    (System.nanoTime() - startedAt) / 1_000_000));
            operationLog.setCreatedAt(LocalDateTime.now());

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
                operationLog.setUserId(loginUser.getId());
                operationLog.setUsername(loginUser.getUsername());
            }
            operationLogService.save(operationLog);
        } catch (RuntimeException e) {
            // 审计失败不影响主流程
            log.warn("记录管理员操作日志失败：{}", e.getMessage());
        }
    }

    private String resolveModule(String path) {
        if (path.contains("/videos")) {
            return "视频";
        }
        if (path.contains("/episodes")) {
            return "转码";
        }
        if (path.contains("/upload")) {
            return "上传";
        }
        if (path.contains("/categories")) {
            return "分类";
        }
        if (path.contains("/operation-logs")) {
            return "日志";
        }
        return "其他";
    }

    private String resolveAction(String method, String path) {
        if (path.contains("/videos")) {
            if ("POST".equals(method)) {
                return "新增视频";
            }
            if ("DELETE".equals(method)) {
                return "删除视频";
            }
            return path.contains("/status") ? "视频上下架" : "编辑视频";
        }
        if (path.contains("/episodes")) {
            if (path.endsWith("/retry")) {
                return "重试转码";
            }
            if (path.endsWith("/cancel")) {
                return "取消转码";
            }
            return "删除剧集";
        }
        if (path.contains("/categories")) {
            if ("POST".equals(method)) {
                return "新增分类";
            }
            if ("DELETE".equals(method)) {
                return "删除分类";
            }
            return "编辑分类";
        }
        if (path.contains("/upload")) {
            return path.endsWith("/complete") ? "完成上传" : "上传操作";
        }
        return method + " 操作";
    }

    private String buildDetail(HttpServletRequest request, String errorMessage) {
        StringBuilder detail = new StringBuilder();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            detail.append('?').append(query);
        }
        String contentType = request.getContentType();
        if (contentType != null) {
            detail.append(detail.isEmpty() ? "" : " ").append('[').append(contentType).append(']');
        }
        if (errorMessage != null) {
            detail.append(detail.isEmpty() ? "" : " ").append("失败：").append(errorMessage);
        }
        return truncate(detail.toString(), 1000);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
