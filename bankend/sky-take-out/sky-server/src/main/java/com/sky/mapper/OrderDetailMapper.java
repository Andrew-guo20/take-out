package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细数据访问接口。
 */
@Mapper
public interface OrderDetailMapper {
    /**
     * 批量保存订单明细。
     */
    void insertBatch(@Param("orderDetailList") List<OrderDetail> orderDetailList);
}
