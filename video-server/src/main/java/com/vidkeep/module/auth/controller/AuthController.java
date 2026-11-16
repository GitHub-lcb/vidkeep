package com.vidkeep.module.auth.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.auth.dto.LoginRequest;
import com.vidkeep.module.auth.dto.LoginResponse;
import com.vidkeep.module.auth.dto.UserInfo;
import com.vidkeep.module.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（文档 6.2 认证模块）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录：POST /api/auth/login，公开接口。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 当前用户：GET /api/auth/me，需登录。
     */
    @GetMapping("/me")
    public Result<UserInfo> me() {
        return Result.ok(authService.currentUser());
    }

    /**
     * 登出：POST /api/auth/logout。无状态 JWT 服务端不维护会话，前端清除 token 即可。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
