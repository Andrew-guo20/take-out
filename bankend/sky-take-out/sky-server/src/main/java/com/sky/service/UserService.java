package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserUpdateDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 更新用户信息
     * @param userUpdateDTO
     */
    void update(UserUpdateDTO userUpdateDTO);
}
