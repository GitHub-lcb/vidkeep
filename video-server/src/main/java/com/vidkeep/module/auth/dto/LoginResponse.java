package com.vidkeep.module.auth.dto;

/**
 * 登录响应：token + 有效期（秒）+ 用户信息。
 */
public record LoginResponse(String token, long expiresIn, UserInfo user) {
}
