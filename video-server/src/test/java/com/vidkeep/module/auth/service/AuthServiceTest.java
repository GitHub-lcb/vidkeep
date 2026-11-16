package com.vidkeep.module.auth.service;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.module.auth.dto.LoginRequest;
import com.vidkeep.module.auth.dto.LoginResponse;
import com.vidkeep.module.user.entity.User;
import com.vidkeep.module.user.service.UserService;
import com.vidkeep.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 登录逻辑单元测试：成功、密码错误、账号不存在、账号禁用。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";
    private static final long EXPIRE_SECONDS = 604800L;

    @Mock
    private UserService userService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthService service = new AuthService(userService, passwordEncoder, new JwtUtil(JWT_SECRET, EXPIRE_SECONDS));
        this.authService = service;
    }

    @Test
    void shouldLoginSuccessfully() {
        when(userService.getByUsername("admin")).thenReturn(activeAdmin());

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));

        assertNotNull(response.token());
        assertEquals(EXPIRE_SECONDS, response.expiresIn());
        assertEquals("admin", response.user().username());
        assertEquals("ADMIN", response.user().role());
    }

    @Test
    void shouldRejectWrongPassword() {
        when(userService.getByUsername("admin")).thenReturn(activeAdmin());

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("admin", "wrong-password")));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), exception.getCode());
    }

    @Test
    void shouldRejectUnknownUsername() {
        when(userService.getByUsername("nobody")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("nobody", "admin123")));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), exception.getCode());
    }

    @Test
    void shouldRejectDisabledUser() {
        User user = activeAdmin();
        user.setStatus(0);
        when(userService.getByUsername("admin")).thenReturn(user);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("admin", "admin123")));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    private User activeAdmin() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setNickname("管理员");
        user.setRole("ADMIN");
        user.setStatus(1);
        return user;
    }
}
