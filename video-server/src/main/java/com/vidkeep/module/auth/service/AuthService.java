package com.vidkeep.module.auth.service;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.module.auth.dto.LoginRequest;
import com.vidkeep.module.auth.dto.LoginResponse;
import com.vidkeep.module.auth.dto.UserInfo;
import com.vidkeep.module.user.entity.User;
import com.vidkeep.module.user.service.UserService;
import com.vidkeep.security.JwtUtil;
import com.vidkeep.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：账号密码登录、获取当前用户。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录：校验账号密码与状态，签发 JWT。
     *
     * <p>安全要点：账号不存在与密码错误返回同一错误码，避免账号枚举。</p>
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = userService.getByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("登录失败（账号或密码错误）：username={}", username);
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            log.warn("登录失败（账号已禁用）：username={}", username);
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        log.info("登录成功：userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, jwtUtil.getExpireSeconds(), UserInfo.from(user));
    }

    /**
     * 当前登录用户信息。
     */
    public UserInfo currentUser() {
        LoginUser loginUser = currentLoginUser();
        User user = userService.getById(loginUser.getId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return UserInfo.from(user);
    }

    private LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }
}
