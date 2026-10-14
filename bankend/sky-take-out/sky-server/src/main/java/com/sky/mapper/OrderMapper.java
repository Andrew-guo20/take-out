package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单数据访问接口。
 */
@Mapper
public interface OrderMapper {
    /**
     * 根据订单号、手机号、状态和下单时间分页查询订单。
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据订单id查询订单。
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 将截止时间之前下单、仍在派送中的订单更新为已完成。
     */
    void completeDeliveryOrder(@Param("orders") Orders orders,
                               @Param("deadline") LocalDateTime deadline);

    /**
     * 取消仍处于待付款、未支付状态且已超时的订单。
     */
    void cancelTimeoutOrder(@Param("orders") Orders orders,
                            @Param("deadline") LocalDateTime deadline);

    /**
     * 查询指定状态且下单时间早于截止时间的订单。
     * @param status 订单状态
     * @param orderTime 下单时间的截止时间，不包含该时刻
     * @return 符合条件的订单列表
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTime(@Param("status") Integer status,
                                       @Param("orderTime") LocalDateTime orderTime);

    /**
     * 根据订单号查询订单。
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 将待付款、未支付的订单更新为已支付，返回更新行数。
     */
    int paySuccess(Orders orders);

    /**
     * 修改订单信息。
     */
    void update(Orders orders);

    /**
     * 保存订单并回填自增主键。
     */
    void insert(Orders orders);

    /**
     * 根据动态条件统计营业额数据
     * @param map 动态查询条件，支持 begin、end、status
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据动态条件统计订单数量
     * @param map 动态查询条件，支持 begin、end、status
     * @return
     */
    Integer countByMap(Map map);
}
