package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

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

    /**
     * 根据动态条件统计用户数量
     * @param begin 起始时间，包含该时刻；为空时不限制
     * @param end 结束时间，不包含该时刻
     * @return
     */
    Integer countByMap(@Param("begin") LocalDateTime begin,
                       @Param("end") LocalDateTime end);
}
