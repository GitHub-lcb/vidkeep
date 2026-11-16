package com.vidkeep.module.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vidkeep.module.user.entity.User;
import com.vidkeep.module.user.mapper.UserMapper;
import com.vidkeep.module.user.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户查询服务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
