package com.vidkeep.module.user.service;

import com.vidkeep.module.user.entity.User;

/**
 * 用户查询服务（M0 仅提供登录所需查询，后续按需扩展）。
 */
public interface UserService {

    /**
     * 按登录名查询，不存在返回 null。
     */
    User getByUsername(String username);

    /**
     * 按主键查询，不存在返回 null。
     */
    User getById(Long id);
}
