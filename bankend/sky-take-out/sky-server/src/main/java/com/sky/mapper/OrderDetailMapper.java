package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单明细数据访问接口。
 */
@Mapper
public interface OrderDetailMapper {
    /**
     * 根据订单id查询订单明细。
     */
    @Select("select * from order_detail where order_id = #{orderId} order by id")
    List<OrderDetail> getByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量保存订单明细。
     */
    void insertBatch(@Param("orderDetailList") List<OrderDetail> orderDetailList);

    /**
     * 统计指定时间区间内销量前十的商品。
     *
     * @param begin 起始时间，包含该时刻
     * @param end 结束时间，不包含该时刻
     * @param status 订单状态，只统计该状态的订单明细
     * @return 商品销量列表，按销量降序、同销量按名称升序排列，最多十条
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                      @Param("end") LocalDateTime end,
                                      @Param("status") Integer status);
}
