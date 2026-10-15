package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.alibaba.fastjson.JSONObject;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal TEST_PAYMENT_AMOUNT = new BigDecimal("0.01");

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private WebSocketServer webSocketServer;

    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        ordersPageQueryDTO.setUserId(getCurrentUserId());
        if (ordersPageQueryDTO.getPage() < 1 || ordersPageQueryDTO.getPageSize() < 1) {
            throw new OrderBusinessException("分页参数必须大于0");
        }
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> records = new ArrayList<>();
        for (Orders orders : page.getResult()) {
            records.add(toOrderVO(orders));
        }
        return new PageResult(page.getTotal(), records);
    }

    @Override
    public OrderVO userDetails(Long id) {
        return toOrderVO(getUserOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userCancel(Long id) throws Exception {
        Long userId = getCurrentUserId();
        Orders orders = getOrderForUpdate(id);
        if (!userId.equals(orders.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        boolean unpaidOrder = Orders.PENDING_PAYMENT.equals(orders.getStatus())
                && Orders.UN_PAID.equals(orders.getPayStatus());
        boolean waitingOrder = Orders.TO_BE_CONFIRMED.equals(orders.getStatus())
                && Orders.PAID.equals(orders.getPayStatus());
        if (!unpaidOrder && !waitingOrder) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        cancelOrder(orders, "用户主动取消", null);
    }

    @Override
    @Transactional
    public void repetition(Long id) {
        Orders orders = getUserOrder(id);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        if (details.isEmpty()) {
            throw new OrderBusinessException("订单明细为空，无法再来一单");
        }
        for (OrderDetail detail : details) {
            ShoppingCart cart = ShoppingCart.builder()
                    .userId(orders.getUserId())
                    .dishId(detail.getDishId())
                    .setmealId(detail.getSetmealId())
                    .dishFlavor(detail.getDishFlavor())
                    .number(detail.getNumber())
                    .createTime(LocalDateTime.now())
                    .build();
            if (detail.getDishId() != null) {
                Dish dish = dishMapper.getById(detail.getDishId());
                if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                    throw new ShoppingCartBusinessException("商品已停售或不存在：" + detail.getName());
                }
                cart.setName(dish.getName());
                cart.setAmount(dish.getPrice());
                cart.setImage(dish.getImage());
            } else {
                Setmeal setmeal = setmealMapper.getById(detail.getSetmealId());
                if (setmeal == null || !StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                    throw new ShoppingCartBusinessException("商品已停售或不存在：" + detail.getName());
                }
                cart.setName(setmeal.getName());
                cart.setAmount(setmeal.getPrice());
                cart.setImage(setmeal.getImage());
            }
            ShoppingCart existing = shoppingCartMapper.getByProduct(cart);
            if (existing == null) {
                shoppingCartMapper.insert(cart);
            } else {
                cart.setId(existing.getId());
                shoppingCartMapper.addNumber(cart);
            }
        }
    }

    private Long getCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }

    private Orders getUserOrder(Long id) {
        Long userId = getCurrentUserId();
        Orders orders = orderMapper.getById(id);
        if (orders == null || !userId.equals(orders.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }

    @Override
    public OrderVO details(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return toOrderVO(orders);
    }

    private OrderVO toOrderVO(Orders orders) {
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
        return orderVO;
    }

    @Override
    @Transactional
    public void delivery(Long id) {
        Orders orders = getOrderForUpdate(id);
        if (!Orders.CONFIRMED.equals(orders.getStatus())
                || !Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orderMapper.update(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).build());
    }

    @Override
    @Transactional
    public void complete(Long id) {
        Orders orders = getOrderForUpdate(id);
        if (!Orders.DELIVERY_IN_PROGRESS.equals(orders.getStatus())
                || !Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orderMapper.update(Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = getOrderForUpdate(ordersConfirmDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(orders.getStatus())
                || !Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orderMapper.update(Orders.builder().id(orders.getId()).status(Orders.CONFIRMED).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        if (!StringUtils.hasText(ordersRejectionDTO.getRejectionReason())) {
            throw new OrderBusinessException("拒单原因不能为空");
        }
        Orders orders = getOrderForUpdate(ordersRejectionDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(orders.getStatus())
                || !Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        cancelOrder(orders, null, ordersRejectionDTO.getRejectionReason().trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        if (!StringUtils.hasText(ordersCancelDTO.getCancelReason())) {
            throw new OrderBusinessException("取消原因不能为空");
        }
        Orders orders = getOrderForUpdate(ordersCancelDTO.getId());
        boolean unpaidOrder = Orders.PENDING_PAYMENT.equals(orders.getStatus())
                && Orders.UN_PAID.equals(orders.getPayStatus());
        boolean paidOrder = (Orders.TO_BE_CONFIRMED.equals(orders.getStatus())
                || Orders.CONFIRMED.equals(orders.getStatus())
                || Orders.DELIVERY_IN_PROGRESS.equals(orders.getStatus()))
                && Orders.PAID.equals(orders.getPayStatus());
        if (!unpaidOrder && !paidOrder) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        cancelOrder(orders, ordersCancelDTO.getCancelReason().trim(), null);
    }

    private Orders getOrderForUpdate(Long id) {
        Orders orders = id == null ? null : orderMapper.getByIdForUpdate(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }

    private void cancelOrder(Orders orders, String cancelReason, String rejectionReason) throws Exception {
        boolean paid = Orders.PAID.equals(orders.getPayStatus());
        Orders cancelled = Orders.builder()
                .id(orders.getId())
                .status(Orders.CANCELLED)
                .cancelReason(cancelReason)
                .rejectionReason(rejectionReason)
                .cancelTime(LocalDateTime.now())
                .build();
        if (paid) {
            cancelled.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(cancelled);

        if (paid) {
            // 固定退款单号保证重试幂等，金额与当前 0.01 元测试支付保持一致。
            JSONObject refund = JSONObject.parseObject(weChatPayUtil.refund(
                    orders.getNumber(), orders.getNumber(), TEST_PAYMENT_AMOUNT, TEST_PAYMENT_AMOUNT));
            if (refund == null || (!"SUCCESS".equals(refund.getString("status"))
                    && !"PROCESSING".equals(refund.getString("status")))) {
                throw new OrderBusinessException("退款申请失败，请稍后重试");
            }
        }
    }

    /**
     * 管理端订单条件分页查询，补充列表展示所需的菜品信息。
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> records = new ArrayList<>();
        for (Orders orders : page.getResult()) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            List<OrderDetail> details = orderDetailMapper.getByOrderId(orders.getId());
            orderVO.setOrderDishes(details.stream()
                    .map(detail -> detail.getName() + "*" + detail.getNumber())
                    .collect(Collectors.joining(";")));
            records.add(orderVO);
        }
        return new PageResult(page.getTotal(), records);
    }

    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO statistics = new OrderStatisticsVO();
        statistics.setToBeConfirmed(orderMapper.countByMap(Collections.singletonMap("status", Orders.TO_BE_CONFIRMED)));
        statistics.setConfirmed(orderMapper.countByMap(Collections.singletonMap("status", Orders.CONFIRMED)));
        statistics.setDeliveryInProgress(orderMapper.countByMap(Collections.singletonMap("status", Orders.DELIVERY_IN_PROGRESS)));
        return statistics;
    }

    /**
     * 校验客户订单后向管理端发送催单提醒。
     */
    @Override
    public void reminder(Long id) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        Orders orders = orderMapper.getById(id);
        if (orders == null || !userId.equals(orders.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PAID.equals(orders.getPayStatus())
                || (!Orders.TO_BE_CONFIRMED.equals(orders.getStatus())
                && !Orders.CONFIRMED.equals(orders.getStatus())
                && !Orders.DELIVERY_IN_PROGRESS.equals(orders.getStatus()))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        JSONObject message = new JSONObject();
        message.put("type", 2);
        message.put("orderId", orders.getId());
        message.put("content", "订单号：" + orders.getNumber());
        webSocketServer.sendToAllClient(message.toJSONString());
    }

    /**
     * 订单支付，沿用课程资料的 0.01 元测试金额。
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),// 商户订单号
                TEST_PAYMENT_AMOUNT,// 支付金额，单位 元
                "苍穹外卖订单",// 商业描述
                user.getOpenid());// 微信用户的openid
        if ("ORDERPAID".equals(jsonObject.getString("code"))) {
            throw new OrderBusinessException("该订单已支付");
        }
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        return vo;
    }

    /**
     * 支付成功后更新订单，并向管理端发送来单提醒。
     */
    @Override
    public void paySuccess(String outTradeNo) {
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException("订单不存在");
        }
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        // 条件更新保证重复或并发回调只触发一次来单提醒。
        if (orderMapper.paySuccess(orders) == 0) {
            return;
        }

        JSONObject message = new JSONObject();
        message.put("type", 1);
        message.put("orderId", ordersDB.getId());
        message.put("content", "订单号：" + outTradeNo);
        webSocketServer.sendToAllClient(message.toJSONString());
    }

    /**
     * 用户下单，订单、明细和购物车操作在同一事务中完成。
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 处理各种业务异常（地址簿为空、购物车数据为空）
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        if (ordersSubmitDTO.getAddressBookId() == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        // 收货地址必须存在，并且属于当前登录用户。
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        if (ordersSubmitDTO.getAmount() == null || ordersSubmitDTO.getAmount().signum() < 0) {
            throw new OrderBusinessException("订单金额不能为空或为负数");
        }

        // 查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.listByUserId(userId);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setNumber(UUID.randomUUID().toString().replace("-", ""));
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        // 保存下单时的收货地址快照，后续修改地址簿不影响订单。
        orders.setAddress(String.join("",
                addressBook.getProvinceName() == null ? "" : addressBook.getProvinceName(),
                addressBook.getCityName() == null ? "" : addressBook.getCityName(),
                addressBook.getDistrictName() == null ? "" : addressBook.getDistrictName(),
                addressBook.getDetail() == null ? "" : addressBook.getDetail()));
        orderMapper.insert(orders);

        // 向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart shoppingCart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            // 不复制购物车主键，订单明细使用自己的自增主键。
            BeanUtils.copyProperties(shoppingCart, orderDetail, "id");
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 封装Vo返回接口
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }
}
