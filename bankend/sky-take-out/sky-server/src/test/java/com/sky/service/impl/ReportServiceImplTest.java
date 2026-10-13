package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.vo.TurnoverReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final ReportServiceImpl service = new ReportServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
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

        verify(orderMapper).sumByMap(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        assertEquals("2026-10-13", report.getDateList());
        assertEquals("0.0", report.getTurnoverList());
    }

    @Test
    void returnsEveryDateIncludingBothEndsAndFillsMissingTurnoverWithZero() {
        when(orderMapper.sumByMap(any(), any(), any())).thenReturn(null, 125.5, null);

        TurnoverReportVO report = service.getTurnoverStatistics(
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13));

        assertEquals("2026-10-11,2026-10-12,2026-10-13", report.getDateList());
        assertEquals("0.0,125.5,0.0", report.getTurnoverList());
    }
}
