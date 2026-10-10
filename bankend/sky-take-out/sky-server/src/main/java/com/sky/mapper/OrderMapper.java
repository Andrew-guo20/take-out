package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问接口。
 */
@Mapper
public interface OrderMapper {
    /**
     * 保存订单并回填自增主键。
     */
    void insert(Orders orders);
}
