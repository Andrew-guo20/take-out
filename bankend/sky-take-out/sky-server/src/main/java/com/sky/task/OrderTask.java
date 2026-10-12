package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());

        // 查询下单超过 15 分钟、仍处于待付款状态的订单。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, deadline);
        for (Orders orders : ordersList) {
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason("支付超时，自动取消");
            orders.setCancelTime(now);
            // 更新时再次校验状态，跳过查询后已支付的订单。
            orderMapper.cancelTimeoutOrder(orders, deadline);
        }
    }

    /**
     * 处理一直处于派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨1点触发一次
    public void processDeliverOrder(){
        log.info("定时处理处于派送中的订单:{}",LocalDateTime.now());
        // 查询当天零点之前下单、仍处于派送中的订单。
        LocalDateTime deadline = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, deadline);
        for (Orders orders : ordersList) {
            orders.setStatus(Orders.COMPLETED);
            // 更新时再次校验派送状态，避免覆盖已取消或已完成的订单。
            orderMapper.completeDeliveryOrder(orders, deadline);
        }
    }
}
