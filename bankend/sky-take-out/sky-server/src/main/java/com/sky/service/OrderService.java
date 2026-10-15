package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;

public interface OrderService {

    /**
     * 分页查询当前用户的订单及菜品明细。
     */
    PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 查询当前用户的订单详情。
     */
    OrderVO userDetails(Long id);

    /**
     * 用户取消待付款或待接单订单，已支付时申请退款。
     */
    void userCancel(Long id) throws Exception;

    /**
     * 将历史订单中的商品按当前价格加入当前用户的购物车。
     */
    void repetition(Long id);

    /**
     * 查询订单及菜品明细。
     */
    OrderVO details(Long id);

    /**
     * 派送已支付、已接单的订单。
     */
    void delivery(Long id);

    /**
     * 完成已支付、派送中的订单并记录送达时间。
     */
    void complete(Long id);

    /**
     * 接受已支付、待接单的订单。
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒绝待接单的订单并申请退款。
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 取消未完成的订单，已支付时申请退款。
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

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
