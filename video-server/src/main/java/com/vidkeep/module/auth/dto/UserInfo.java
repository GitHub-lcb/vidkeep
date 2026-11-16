package com.vidkeep.module.auth.dto;

import com.vidkeep.module.user.entity.User;

/**
 * 对外暴露的用户信息（不含密码）。
 */
public record UserInfo(Long id, String username, String nickname, String role) {

    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
