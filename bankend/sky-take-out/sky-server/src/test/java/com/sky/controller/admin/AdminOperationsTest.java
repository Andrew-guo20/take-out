package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.exception.PasswordErrorException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.EmployeeMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.impl.EmployeeServiceImpl;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.utils.WeChatPayUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOperationsTest {

    private final WeChatPayUtil weChatPayUtil = mock(WeChatPayUtil.class);
    private final EmployeeServiceImpl employeeService = new EmployeeServiceImpl();
    private Connection connection;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        BaseContext.setCurrentId(7L);
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:admin-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        connection = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table orders (id bigint primary key, number varchar(32), status int, "
                + "pay_status int, amount decimal(10,2), cancel_reason varchar(255), "
                + "rejection_reason varchar(255), cancel_time timestamp, delivery_time timestamp, "
                + "phone varchar(11), address varchar(255), consignee varchar(32), order_time timestamp)");
        jdbc.update("insert into orders (id, number, status, pay_status, amount) values (42, 'order-42', 2, 1, 99.99)");
        jdbc.execute("create table order_detail (id bigint primary key, order_id bigint, name varchar(32), "
                + "dish_id bigint, setmeal_id bigint, dish_flavor varchar(255), number int, "
                + "amount decimal(10,2), image varchar(255))");
        jdbc.execute("create table employee (id bigint primary key, username varchar(32), password varchar(32), "
                + "status int, update_time timestamp, update_user bigint)");
        String password = DigestUtils.md5DigestAsHex("Old123".getBytes());
        jdbc.update("insert into employee (id, username, password, status) values (7, 'alice', ?, 1), (8, 'bob', ?, 1)",
                password, password);

        Configuration configuration = new Configuration(new Environment("test",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : new String[]{"mapper/OrderMapper.xml", "mapper/OrderDetailMapper.xml",
                "mapper/EmployeeMapper.xml"}) {
            try (InputStream xml = getClass().getResourceAsStream("/" + resource)) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        OrderServiceImpl orderService = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "orderMapper", session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(orderService, "orderDetailMapper", session.getMapper(OrderDetailMapper.class));
        ReflectionTestUtils.setField(orderService, "weChatPayUtil", weChatPayUtil);
        ProxyFactory proxy = new ProxyFactory(orderService);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        OrderController orderController = new OrderController();
        ReflectionTestUtils.setField(orderController, "orderService", proxy.getProxy());
        ReflectionTestUtils.setField(employeeService, "employeeMapper", session.getMapper(EmployeeMapper.class));
        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "employeeService", employeeService);
        mvc = MockMvcBuilders.standaloneSetup(orderController, employeeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper())).build();
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"status\":\"SUCCESS\"}");
    }

    @AfterEach
    void tearDown() throws Exception {
        BaseContext.removeCurrentId();
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void confirmationUsesServerStatusAndCannotBeRepeated() throws Exception {
        orderAction("confirm", "{\"id\":42,\"status\":6}").andExpect(jsonPath("$.code").value(1));
        assertEquals(3, order().get("status"));
        orderAction("confirm", "{\"id\":42}").andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(weChatPayUtil);
    }

    @Test
    void detailsReturnsOrderSnapshotAndOnlyItsOwnItemsInStableOrder() throws Exception {
        jdbc.update("update orders set phone='13800138000', address='Test address', consignee='Alice', "
                + "order_time='2026-10-15 12:30:00' where id=42");
        jdbc.update("insert into order_detail values "
                + "(2, 42, 'Meal', null, 8, null, 1, 20.00, '/meal.png'), "
                + "(1, 42, 'Fish', 7, null, 'Spicy', 2, 39.99, '/fish.png'), "
                + "(3, 99, 'Other', 9, null, null, 1, 10.00, '/other.png')");
        mvc.perform(get("/admin/order/details/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.number").value("order-42"))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.payStatus").value(1))
                .andExpect(jsonPath("$.data.amount").value(99.99))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("Test address"))
                .andExpect(jsonPath("$.data.consignee").value("Alice"))
                .andExpect(jsonPath("$.data.orderTime").value("2026-10-15 12:30"))
                .andExpect(jsonPath("$.data.orderDetailList.length()").value(2))
                .andExpect(jsonPath("$.data.orderDetailList[0].name").value("Fish"))
                .andExpect(jsonPath("$.data.orderDetailList[0].dishId").value(7))
                .andExpect(jsonPath("$.data.orderDetailList[0].dishFlavor").value("Spicy"))
                .andExpect(jsonPath("$.data.orderDetailList[0].number").value(2))
                .andExpect(jsonPath("$.data.orderDetailList[0].amount").value(39.99))
                .andExpect(jsonPath("$.data.orderDetailList[0].image").value("/fish.png"))
                .andExpect(jsonPath("$.data.orderDetailList[1].name").value("Meal"))
                .andExpect(jsonPath("$.data.orderDetailList[1].setmealId").value(8));
    }

    @Test
    void detailsReturnsEmptyListForOrderWithoutItems() throws Exception {
        mvc.perform(get("/admin/order/details/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderDetailList").isEmpty());
    }

    @Test
    void detailsRejectsMissingOrder() throws Exception {
        mvc.perform(get("/admin/order/details/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_NOT_FOUND));
    }

    @Test
    void paidOrderCanBeConfirmedDeliveredAndCompletedWithoutRepeatingOrCancelling() throws Exception {
        orderAction("confirm", "{\"id\":42}").andExpect(jsonPath("$.code").value(1));
        orderAction("delivery/42", "").andExpect(jsonPath("$.code").value(1));
        Map<String, Object> delivering = order();
        assertEquals(4, delivering.get("status"));
        assertNull(delivering.get("delivery_time"));
        orderAction("delivery/42", "").andExpect(jsonPath("$.code").value(0));
        assertEquals(delivering, order());

        orderAction("complete/42", "").andExpect(jsonPath("$.code").value(1));
        Map<String, Object> completed = order();
        assertEquals(5, completed.get("status"));
        assertEquals(1, completed.get("pay_status"));
        assertEquals(new BigDecimal("99.99"), completed.get("amount"));
        assertNotNull(completed.get("delivery_time"));
        orderAction("complete/42", "").andExpect(jsonPath("$.code").value(0));
        orderAction("cancel", "{\"id\":42,\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(completed, order());
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @CsvSource({"delivery,1,0", "delivery,2,1", "delivery,4,1", "delivery,5,1", "delivery,6,2",
            "delivery,3,0", "delivery,3,2", "complete,1,0", "complete,2,1", "complete,3,1",
            "complete,5,1", "complete,6,2", "complete,4,0", "complete,4,2"})
    void deliveryAndCompletionRejectInvalidStatesWithoutChangingOrder(String action, int state, int payState)
            throws Exception {
        jdbc.update("update orders set status=?, pay_status=?, delivery_time='2026-10-01 12:00:00' where id=42",
                state, payState);
        Map<String, Object> original = order();
        orderAction(action + "/42", "")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
        assertEquals(original, order());
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivery", "complete"})
    void deliveryAndCompletionRejectMissingOrder(String action) throws Exception {
        orderAction(action + "/999", "")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_NOT_FOUND));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @CsvSource({"delivery,3", "complete,4"})
    void concurrentDeliveryOrCompletionCannotOverwriteCancellation(String action, int state) throws Exception {
        jdbc.update("update orders set status=? where id=42", state);
        CountDownLatch refundStarted = new CountDownLatch(1);
        CountDownLatch finishRefund = new CountDownLatch(1);
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            refundStarted.countDown();
            assertTrue(finishRefund.await(5, TimeUnit.SECONDS));
            return "{\"status\":\"SUCCESS\"}";
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResultActions> cancellation = executor.submit(() -> orderAction("cancel",
                    "{\"id\":42,\"cancelReason\":\"customer request\"}"));
            assertTrue(refundStarted.await(5, TimeUnit.SECONDS));
            Future<ResultActions> transition = executor.submit(() -> orderAction(action + "/42", ""));
            assertThrows(TimeoutException.class, () -> transition.get(200, TimeUnit.MILLISECONDS));
            finishRefund.countDown();
            cancellation.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(1));
            transition.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
            assertEquals(6, order().get("status"));
            assertEquals(2, order().get("pay_status"));
            assertNull(order().get("delivery_time"));
            verify(weChatPayUtil, times(1)).refund(anyString(), anyString(), any(), any());
        } finally {
            finishRefund.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"confirm,1,0", "confirm,3,1", "confirm,4,1", "confirm,5,1", "confirm,6,2",
            "confirm,2,0", "confirm,2,2", "rejection,1,0", "rejection,3,1", "rejection,4,1",
            "rejection,5,1", "rejection,6,2", "rejection,2,0", "rejection,2,2"})
    void rejectsInvalidStateWithoutChangingOrder(String action, int state, int payState) throws Exception {
        jdbc.update("update orders set status=?, pay_status=? where id=42", state, payState);
        orderAction(action, "{\"id\":42,\"rejectionReason\":\"sold out\"}")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
        assertEquals(state, order().get("status"));
        assertEquals(payState, order().get("pay_status"));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "rejection", "cancel"})
    void rejectsMissingOrder(String action) throws Exception {
        orderAction(action, "{\"id\":999,\"rejectionReason\":\"sold out\",\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_NOT_FOUND));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "rejection", "cancel"})
    void rejectsMissingOrderId(String action) throws Exception {
        orderAction(action, "{\"rejectionReason\":\"sold out\",\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_NOT_FOUND));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "PROCESSING"})
    void rejectionRefundsTestPaymentAndRecordsReason(String refundStatus) throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"status\":\"" + refundStatus + "\"}");
        orderAction("rejection", "{\"id\":42,\"rejectionReason\":\"sold out\"}")
                .andExpect(jsonPath("$.code").value(1));
        Map<String, Object> order = order();
        assertEquals(6, order.get("status"));
        assertEquals(2, order.get("pay_status"));
        assertEquals("sold out", order.get("rejection_reason"));
        assertNotNull(order.get("cancel_time"));
        verify(weChatPayUtil).refund("order-42", "order-42", new BigDecimal("0.01"), new BigDecimal("0.01"));
    }

    @Test
    void cancellationOfUnpaidOrderDoesNotRefund() throws Exception {
        jdbc.update("update orders set status=1, pay_status=0 where id=42");
        orderAction("cancel", "{\"id\":42,\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(6, order().get("status"));
        assertEquals(0, order().get("pay_status"));
        assertEquals("customer request", order().get("cancel_reason"));
        assertNotNull(order().get("cancel_time"));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void cancellationRefundsPaidActiveOrderOnlyOnce(int state) throws Exception {
        jdbc.update("update orders set status=? where id=42", state);
        String body = "{\"id\":42,\"cancelReason\":\"customer request\"}";
        orderAction("cancel", body).andExpect(jsonPath("$.code").value(1));
        orderAction("cancel", body).andExpect(jsonPath("$.code").value(0));
        assertEquals(6, order().get("status"));
        assertEquals(2, order().get("pay_status"));
        verify(weChatPayUtil, times(1)).refund(anyString(), anyString(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,0", "2,2", "3,0", "4,0", "5,1", "6,0", "6,2"})
    void cancellationRejectsTerminalOrInconsistentState(int state, int payState) throws Exception {
        jdbc.update("update orders set status=?, pay_status=? where id=42", state, payState);
        orderAction("cancel", "{\"id\":42,\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
        assertEquals(state, order().get("status"));
        assertEquals(payState, order().get("pay_status"));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @CsvSource({"rejection,rejectionReason", "cancel,cancelReason"})
    void rejectsBlankReason(String action, String field) throws Exception {
        orderAction(action, "{\"id\":42,\"" + field + "\":\"   \"}")
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(2, order().get("status"));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"NOT_ENOUGH\"}", "{\"status\":\"ABNORMAL\"}",
            "{\"status\":\"CLOSED\"}", "null"})
    void refundFailureRollsBackCancellation(String response) throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenReturn(response);
        orderAction("cancel", "{\"id\":42,\"cancelReason\":\"customer request\"}")
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(2, order().get("status"));
        assertEquals(1, order().get("pay_status"));
        assertNull(order().get("cancel_time"));
    }

    @Test
    void checkedRefundExceptionRollsBackRejection() throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenThrow(new IOException("payment gateway unavailable"));
        assertThrows(IOException.class,
                () -> orderAction("rejection", "{\"id\":42,\"rejectionReason\":\"sold out\"}"));
        assertEquals(2, order().get("status"));
        assertEquals(1, order().get("pay_status"));
        assertNull(order().get("rejection_reason"));
    }

    @Test
    void concurrentRejectionsRefundOnlyOnce() throws Exception {
        CountDownLatch refundStarted = new CountDownLatch(1);
        CountDownLatch finishRefund = new CountDownLatch(1);
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            refundStarted.countDown();
            assertTrue(finishRefund.await(5, TimeUnit.SECONDS));
            return "{\"status\":\"SUCCESS\"}";
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            String body = "{\"id\":42,\"rejectionReason\":\"sold out\"}";
            Future<ResultActions> first = executor.submit(() -> orderAction("rejection", body));
            assertTrue(refundStarted.await(5, TimeUnit.SECONDS));
            Future<ResultActions> second = executor.submit(() -> orderAction("rejection", body));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            finishRefund.countDown();
            first.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(1));
            second.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(0));
            verify(weChatPayUtil, times(1)).refund(anyString(), anyString(), any(), any());
        } finally {
            finishRefund.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void passwordChangeWorksWithoutClientEmployeeIdAndNewPasswordCanLogIn() throws Exception {
        changePassword("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}")
                .andExpect(jsonPath("$.code").value(1));
        Map<String, Object> employee = jdbc.queryForMap("select * from employee where id=7");
        assertEquals(DigestUtils.md5DigestAsHex("New456".getBytes()), employee.get("password"));
        assertEquals(7L, employee.get("update_user"));
        assertNotNull(employee.get("update_time"));
        EmployeeLoginDTO login = new EmployeeLoginDTO();
        login.setUsername("alice");
        login.setPassword("New456");
        assertEquals(7L, employeeService.login(login).getId());
        login.setPassword("Old123");
        assertThrows(PasswordErrorException.class, () -> employeeService.login(login));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"oldPassword\":\"wrong\",\"newPassword\":\"New456\"}",
            "{\"oldPassword\":\"Old123\",\"newPassword\":\"\"}",
            "{\"oldPassword\":\"Old123\",\"newPassword\":\"short\"}",
            "{\"oldPassword\":\"Old123\",\"newPassword\":\"invalid!\"}",
            "{\"oldPassword\":\"Old123\",\"newPassword\":\"123456789012345678901\"}",
            "{\"oldPassword\":\"Old123\"}",
            "{\"newPassword\":\"New456\"}",
            "{\"empId\":8,\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}"})
    void invalidPasswordChangeDoesNotAlterAnyAccount(String body) throws Exception {
        changePassword(body).andExpect(jsonPath("$.code").value(0));
        assertEquals(2, jdbc.queryForObject("select count(*) from employee where password=?",
                Integer.class, DigestUtils.md5DigestAsHex("Old123".getBytes())));
    }

    @Test
    void passwordChangeRejectsUnauthenticatedUser() throws Exception {
        BaseContext.removeCurrentId();
        changePassword("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.USER_NOT_LOGIN));
    }

    @Test
    void passwordChangeAcceptsMatchingEmployeeIdAndMaximumLength() throws Exception {
        changePassword("{\"empId\":7,\"oldPassword\":\"Old123\",\"newPassword\":\"12345678901234567890\"}")
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(DigestUtils.md5DigestAsHex("12345678901234567890".getBytes()),
                jdbc.queryForObject("select password from employee where id=7", String.class));
    }

    @Test
    void passwordChangeRejectsDisabledEmployeeAndStaleOldPassword() throws Exception {
        jdbc.update("update employee set status=0 where id=7");
        String body = "{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}";
        changePassword(body).andExpect(jsonPath("$.code").value(0));
        jdbc.update("update employee set status=1 where id=7");
        changePassword(body).andExpect(jsonPath("$.code").value(1));
        changePassword("{\"oldPassword\":\"Old123\",\"newPassword\":\"Other789\"}")
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(DigestUtils.md5DigestAsHex("New456".getBytes()),
                jdbc.queryForObject("select password from employee where id=7", String.class));
    }

    private ResultActions orderAction(String action, String body) throws Exception {
        return mvc.perform(put("/admin/order/" + action).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions changePassword(String body) throws Exception {
        return mvc.perform(put("/admin/employee/editPassword").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Map<String, Object> order() {
        return jdbc.queryForMap("select * from orders where id=42");
    }
}
