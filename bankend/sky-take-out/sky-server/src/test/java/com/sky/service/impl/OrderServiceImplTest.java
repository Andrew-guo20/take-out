package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final WebSocketServer webSocketServer = mock(WebSocketServer.class);
    private final OrderServiceImpl service = new OrderServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "webSocketServer", webSocketServer);
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
