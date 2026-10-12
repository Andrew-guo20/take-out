package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据用户 ID 查询微信支付所需的用户信息。
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 根据openid查询用户
     *
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 更新用户数据
     *
     * @param user
     */
    void update(User user);

    /**
     * 插入用户数据
     *
     * @param user
     */
    void insert(User user);
}
