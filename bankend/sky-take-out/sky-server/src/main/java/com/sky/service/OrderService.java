package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;

public interface OrderService {

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
