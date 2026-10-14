package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OrderDetailMapper orderDetailMapper = mock(OrderDetailMapper.class);
    private final ReportServiceImpl service = new ReportServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", orderDetailMapper);
    }

    @ParameterizedTest
    @MethodSource("missingDates")
    void rejectsMissingDatesBeforeQuerying(LocalDate begin, LocalDate end) {
        BaseException exception = assertThrows(BaseException.class,
                () -> service.getTurnoverStatistics(begin, end));

        assertEquals("\u5f00\u59cb\u65e5\u671f\u548c\u7ed3\u675f\u65e5\u671f\u4e0d\u80fd\u4e3a\u7a7a", exception.getMessage());
        verifyNoInteractions(orderMapper);
    }

    static Stream<Arguments> missingDates() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        return Stream.of(Arguments.of(null, date), Arguments.of(date, null), Arguments.of(null, null));
    }

    @Test
    void rejectsReversedRangeBeforeQuerying() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        BaseException exception = assertThrows(BaseException.class,
                () -> service.getTurnoverStatistics(date, date.minusDays(1)));

        assertEquals("\u5f00\u59cb\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u7ed3\u675f\u65e5\u671f", exception.getMessage());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void usesWholeDayInterval() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        TurnoverReportVO report = service.getTurnoverStatistics(date, date);

        Map<String, Object> expected = new HashMap<>();
        expected.put("begin", date.atStartOfDay());
        expected.put("end", date.plusDays(1).atStartOfDay());
        expected.put("status", Orders.COMPLETED);
        verify(orderMapper).sumByMap(expected);
        assertEquals("2026-10-13", report.getDateList());
        assertEquals("0.0", report.getTurnoverList());
    }

    @Test
    void returnsEveryDateIncludingBothEndsAndFillsMissingTurnoverWithZero() {
        when(orderMapper.sumByMap(anyMap())).thenReturn(null, 125.5, null);

        TurnoverReportVO report = service.getTurnoverStatistics(
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13));

        assertEquals("2026-10-11,2026-10-12,2026-10-13", report.getDateList());
        assertEquals("0.0,125.5,0.0", report.getTurnoverList());
    }

    @ParameterizedTest
    @MethodSource("missingDates")
    void userStatisticsRejectMissingDatesBeforeQuerying(LocalDate begin, LocalDate end) {
        assertThrows(BaseException.class, () -> service.getUserStatistics(begin, end));

        verifyNoInteractions(userMapper);
    }

    @Test
    void userStatisticsRejectReversedRangeBeforeQuerying() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        assertThrows(BaseException.class, () -> service.getUserStatistics(date, date.minusDays(1)));

        verifyNoInteractions(userMapper);
    }

    @Test
    void userStatisticsReturnSingleDayZerosAndUseWholeDayBoundaries() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        UserReportVO report = service.getUserStatistics(date, date);

        verify(userMapper).countByMap(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        verify(userMapper).countByMap(null, date.plusDays(1).atStartOfDay());
        assertEquals("2026-10-13", report.getDateList());
        assertEquals("0", report.getNewUserList());
        assertEquals("0", report.getTotalUserList());
    }

    @Test
    void userStatisticsReturnDailyNewAndCumulativeCountsIncludingBothEnds() {
        LocalDate begin = LocalDate.of(2026, 10, 11);
        for (int day = 0; day < 3; day++) {
            LocalDate date = begin.plusDays(day);
            when(userMapper.countByMap(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                    .thenReturn(day == 0 ? 1 : day == 1 ? 0 : 2);
            when(userMapper.countByMap(null, date.plusDays(1).atStartOfDay()))
                    .thenReturn(day < 2 ? 6 : 8);
        }

        UserReportVO report = service.getUserStatistics(begin, begin.plusDays(2));

        assertEquals("2026-10-11,2026-10-12,2026-10-13", report.getDateList());
        assertEquals("1,0,2", report.getNewUserList());
        assertEquals("6,6,8", report.getTotalUserList());
    }

    @ParameterizedTest
    @MethodSource("missingDates")
    void orderStatisticsRejectMissingDatesBeforeQuerying(LocalDate begin, LocalDate end) {
        assertThrows(BaseException.class, () -> service.getOrderStatistics(begin, end));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void orderStatisticsRejectReversedRangeBeforeQuerying() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        assertThrows(BaseException.class, () -> service.getOrderStatistics(date, date.minusDays(1)));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void orderStatisticsReturnDailyAndTotalsIncludingBothEnds() {
        LocalDate begin = LocalDate.of(2026, 10, 11);
        when(orderMapper.countByMap(anyMap())).thenReturn(2, 1, 0, 0, 3, 3);

        OrderReportVO report = service.getOrderStatistics(begin, begin.plusDays(2));

        assertEquals("2026-10-11,2026-10-12,2026-10-13", report.getDateList());
        assertEquals("2,0,3", report.getOrderCountList());
        assertEquals("1,0,3", report.getValidOrderCountList());
        assertEquals(5, report.getTotalOrderCount());
        assertEquals(4, report.getValidOrderCount());
        assertEquals(0.8, report.getOrderCompletionRate());
    }

    @ParameterizedTest
    @MethodSource("missingDates")
    void top10RejectMissingDatesBeforeQuerying(LocalDate begin, LocalDate end) {
        assertThrows(BaseException.class, () -> service.getTop10(begin, end));

        verifyNoInteractions(orderDetailMapper);
    }

    @Test
    void top10RejectReversedRangeBeforeQuerying() {
        LocalDate date = LocalDate.of(2026, 10, 13);

        assertThrows(BaseException.class, () -> service.getTop10(date, date.minusDays(1)));

        verifyNoInteractions(orderDetailMapper);
    }

    @Test
    void top10UseWholeDayIntervalAndJoinNameAndNumberLists() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        when(orderDetailMapper.getSalesTop10(any(), any(), any())).thenReturn(Arrays.asList(
                GoodsSalesDTO.builder().name("鱼香肉丝").number(12).build(),
                GoodsSalesDTO.builder().name("宫保鸡丁").number(8).build()));

        SalesTop10ReportVO report = service.getTop10(date, date);

        verify(orderDetailMapper).getSalesTop10(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        assertEquals("鱼香肉丝,宫保鸡丁", report.getNameList());
        assertEquals("12,8", report.getNumberList());
    }

    @Test
    void top10ReturnsEmptyListsForRangeWithoutSales() {
        LocalDate begin = LocalDate.of(2026, 9, 30);
        LocalDate end = LocalDate.of(2026, 10, 2);

        SalesTop10ReportVO report = service.getTop10(begin, end);

        verify(orderDetailMapper).getSalesTop10(
                begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        assertEquals("", report.getNameList());
        assertEquals("", report.getNumberList());
    }
}
