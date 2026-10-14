package com.sky.controller.admin;

import com.sky.dto.GoodsSalesDTO;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OrderDetailMapper orderDetailMapper = mock(OrderDetailMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ReportServiceImpl service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", orderDetailMapper);
        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?begin=2026-10-13", "?end=2026-10-13",
            "?begin=2026-10-13&end=2026-10-12"})
    void invalidDatesReturnBusinessErrorWithoutQuerying(String query) throws Exception {
        mvc.perform(get("/admin/report/turnoverStatistics" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void validDatesKeepExistingResponseFormat() throws Exception {
        when(orderMapper.sumByMap(anyMap())).thenReturn(null, 125.5, null);

        mvc.perform(get("/admin/report/turnoverStatistics")
                        .param("begin", "2026-10-11").param("end", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.dateList").value("2026-10-11,2026-10-12,2026-10-13"))
                .andExpect(jsonPath("$.data.turnoverList").value("0.0,125.5,0.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?begin=2026-10-13", "?end=2026-10-13",
            "?begin=2026-10-13&end=2026-10-12"})
    void invalidUserStatisticsDatesReturnBusinessError(String query) throws Exception {
        mvc.perform(get("/admin/report/userStatistics" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(userMapper);
    }

    @Test
    void userStatisticsReturnFrontendCompatibleDateAndCountLists() throws Exception {
        when(userMapper.countByMap(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1, 0, 2);
        when(userMapper.countByMap(isNull(), any())).thenReturn(6, 6, 8);

        mvc.perform(get("/admin/report/userStatistics")
                        .param("begin", "2026-10-11").param("end", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.dateList").value("2026-10-11,2026-10-12,2026-10-13"))
                .andExpect(jsonPath("$.data.newUserList").value("1,0,2"))
                .andExpect(jsonPath("$.data.totalUserList").value("6,6,8"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?begin=2026-10-13", "?end=2026-10-13",
            "?begin=2026-10-13&end=2026-10-12"})
    void invalidOrderStatisticsDatesReturnBusinessError(String query) throws Exception {
        mvc.perform(get("/admin/report/ordersStatistics" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void orderStatisticsReturnFrontendCompatibleListsAndTotals() throws Exception {
        when(orderMapper.countByMap(anyMap())).thenReturn(2, 1, 0, 0, 3, 3);

        mvc.perform(get("/admin/report/ordersStatistics")
                        .param("begin", "2026-10-11").param("end", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.dateList").value("2026-10-11,2026-10-12,2026-10-13"))
                .andExpect(jsonPath("$.data.orderCountList").value("2,0,3"))
                .andExpect(jsonPath("$.data.validOrderCountList").value("1,0,3"))
                .andExpect(jsonPath("$.data.totalOrderCount").value(5))
                .andExpect(jsonPath("$.data.validOrderCount").value(4))
                .andExpect(jsonPath("$.data.orderCompletionRate").value(0.8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?begin=2026-10-13", "?end=2026-10-13",
            "?begin=2026-10-13&end=2026-10-12"})
    void invalidTop10DatesReturnBusinessError(String query) throws Exception {
        mvc.perform(get("/admin/report/top10" + query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(orderDetailMapper);
    }

    @Test
    void top10ReturnFrontendCompatibleNameAndNumberLists() throws Exception {
        when(orderDetailMapper.getSalesTop10(any(), any(), any())).thenReturn(Arrays.asList(
                GoodsSalesDTO.builder().name("鱼香肉丝").number(12).build(),
                GoodsSalesDTO.builder().name("宫保鸡丁").number(8).build()));

        mvc.perform(get("/admin/report/top10")
                        .param("begin", "2026-10-13").param("end", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.nameList").value("鱼香肉丝,宫保鸡丁"))
                .andExpect(jsonPath("$.data.numberList").value("12,8"));
    }

    @Test
    void top10ReturnsEmptyListsWhenNoSalesMatch() throws Exception {
        mvc.perform(get("/admin/report/top10")
                        .param("begin", "2026-10-13").param("end", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.nameList").value(""))
                .andExpect(jsonPath("$.data.numberList").value(""));
    }
}
