package com.sky.controller.user;

import com.sky.constant.MessageConstant;
import com.sky.exception.OrderBusinessException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void reminderMatchesMiniProgramGetRequest() throws Exception {
        mvc.perform(get("/user/order/reminder/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(orderService).reminder(42L);
    }

    @Test
    void reminderReturnsBusinessErrorForInvalidOrder() throws Exception {
        doThrow(new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR))
                .when(orderService).reminder(42L);

        mvc.perform(get("/user/order/reminder/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
    }
}
