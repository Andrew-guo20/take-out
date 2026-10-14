package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderStatisticsVO;

public interface OrderService {

    /**
     * 管理端订单条件分页查询。
     */
    PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 查询待接单、待派送和派送中的订单数量。
     */
    OrderStatisticsVO statistics();

    /**
     * 客户催单。
     */
    void reminder(Long id);

    /**
     * 订单支付。
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功后修改订单状态。
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户下单。
     * @param ordersSubmitDTO 下单信息
     * @return 下单结果
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);
}
