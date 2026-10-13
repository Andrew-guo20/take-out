package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final WebSocketServer webSocketServer = mock(WebSocketServer.class);
    private final OrderServiceImpl service = new OrderServiceImpl();

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "webSocketServer", webSocketServer);
    }

    @AfterEach
    void clearCurrentUser() {
        BaseContext.removeCurrentId();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void reminderNotifiesForOwnPaidActiveOrderWithoutChangingIt(int status) {
        when(orderMapper.getById(42L)).thenReturn(Orders.builder()
                .id(42L).number("order-42").userId(7L).status(status).payStatus(Orders.PAID).build());

        service.reminder(42L);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketServer).sendToAllClient(messageCaptor.capture());
        JSONObject message = JSON.parseObject(messageCaptor.getValue());
        assertEquals(2, message.getIntValue("type"));
        assertEquals(42L, message.getLongValue("orderId"));
        assertEquals("\u8ba2\u5355\u53f7\uff1aorder-42", message.getString("content"));
        verify(orderMapper).getById(42L);
        verifyNoMoreInteractions(orderMapper);
    }

    @Test
    void reminderRejectsUnauthenticatedUser() {
        BaseContext.removeCurrentId();

        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> service.reminder(42L));

        assertEquals(MessageConstant.USER_NOT_LOGIN, exception.getMessage());
        verifyNoInteractions(orderMapper, webSocketServer);
    }

    @Test
    void reminderRejectsMissingOrder() {
        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> service.reminder(42L));

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verifyNoInteractions(webSocketServer);
    }

    @Test
    void reminderRejectsAnotherUsersOrder() {
        when(orderMapper.getById(42L)).thenReturn(Orders.builder()
                .id(42L).userId(8L).status(Orders.CONFIRMED).payStatus(Orders.PAID).build());

        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> service.reminder(42L));

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verifyNoInteractions(webSocketServer);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 6})
    void reminderRejectsInactiveOrder(int status) {
        when(orderMapper.getById(42L)).thenReturn(Orders.builder()
                .id(42L).userId(7L).status(status).payStatus(Orders.PAID).build());

        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> service.reminder(42L));

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verifyNoInteractions(webSocketServer);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void reminderRejectsUnpaidOrRefundedOrder(int payStatus) {
        when(orderMapper.getById(42L)).thenReturn(Orders.builder()
                .id(42L).userId(7L).status(Orders.CONFIRMED).payStatus(payStatus).build());

        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> service.reminder(42L));

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verifyNoInteractions(webSocketServer);
    }

    @Test
    void paySuccessUpdatesOrderBeforeSendingReminder() {
        when(orderMapper.getByNumber("order-42")).thenReturn(
                Orders.builder().id(42L).number("order-42").build());
        when(orderMapper.paySuccess(any())).thenReturn(1);

        service.paySuccess("order-42");

        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(orderMapper, webSocketServer);
        inOrder.verify(orderMapper).paySuccess(orderCaptor.capture());
        inOrder.verify(webSocketServer).sendToAllClient(messageCaptor.capture());
        Orders order = orderCaptor.getValue();
        assertEquals(42L, order.getId());
        assertEquals(Orders.TO_BE_CONFIRMED, order.getStatus());
        assertEquals(Orders.PAID, order.getPayStatus());
        assertNotNull(order.getCheckoutTime());
        JSONObject message = JSON.parseObject(messageCaptor.getValue());
        assertEquals(1, message.getIntValue("type"));
        assertEquals(42L, message.getLongValue("orderId"));
        assertEquals("\u8ba2\u5355\u53f7\uff1aorder-42", message.getString("content"));
    }

    @Test
    void paySuccessDoesNotNotifyWhenConditionalUpdateDoesNotChangeOrder() {
        when(orderMapper.getByNumber("order-42")).thenReturn(
                Orders.builder().id(42L).number("order-42").status(Orders.CONFIRMED)
                        .payStatus(Orders.PAID).build());
        when(orderMapper.paySuccess(any())).thenReturn(0);

        service.paySuccess("order-42");

        verifyNoInteractions(webSocketServer);
        verify(orderMapper, never()).update(any());
    }

    @Test
    void paySuccessDoesNotNotifyWhenOrderDoesNotExist() {
        assertThrows(OrderBusinessException.class, () -> service.paySuccess("missing"));

        verify(orderMapper, never()).paySuccess(any());
        verifyNoInteractions(webSocketServer);
    }

    @Test
    void paySuccessDoesNotNotifyWhenDatabaseUpdateFails() {
        when(orderMapper.getByNumber("order-42")).thenReturn(Orders.builder().id(42L).build());
        when(orderMapper.paySuccess(any())).thenThrow(new IllegalStateException("database failure"));

        assertThrows(IllegalStateException.class, () -> service.paySuccess("order-42"));

        verifyNoInteractions(webSocketServer);
    }
}
