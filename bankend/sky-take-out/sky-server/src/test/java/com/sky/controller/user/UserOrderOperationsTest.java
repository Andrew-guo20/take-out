package com.sky.controller.user;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserOrderOperationsTest {

    private final WeChatPayUtil weChatPayUtil = mock(WeChatPayUtil.class);
    private Connection connection;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        BaseContext.setCurrentId(7L);
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:user-orders-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        connection = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table orders (id bigint primary key, user_id bigint, number varchar(32), "
                + "status int, pay_status int, amount decimal(10,2), order_time timestamp, "
                + "phone varchar(11), address varchar(255), consignee varchar(32), "
                + "cancel_reason varchar(255), rejection_reason varchar(255), cancel_time timestamp)");
        jdbc.update("insert into orders (id, user_id, number, status, pay_status, amount, order_time) values "
                + "(42, 7, 'order-42', 2, 1, 39.97, '2026-10-15 12:00:00'), "
                + "(43, 7, 'order-43', 5, 1, 20.00, '2026-10-15 13:00:00'), "
                + "(44, 7, 'order-44', 1, 0, 10.00, '2026-10-15 13:00:00'), "
                + "(99, 8, 'order-99', 2, 1, 200.00, '2026-10-15 14:00:00')");
        jdbc.update("update orders set phone='13800138000', address='Saved address', consignee='Alice' where id=42");
        jdbc.execute("create table order_detail (id bigint primary key, order_id bigint, name varchar(32), "
                + "dish_id bigint, setmeal_id bigint, dish_flavor varchar(255), number int, "
                + "amount decimal(10,2), image varchar(255))");
        jdbc.update("insert into order_detail values "
                + "(1, 42, 'Fish', 11, null, 'Spicy', 2, 9.99, '/old-fish.png'), "
                + "(2, 42, 'Meal', null, 21, null, 1, 19.99, '/old-meal.png'), "
                + "(3, 99, 'Other', 12, null, null, 1, 200.00, '/other.png')");
        jdbc.execute("create table dish (id bigint primary key, name varchar(32), price decimal(10,2), "
                + "image varchar(255), status int)");
        jdbc.update("insert into dish values (11, 'Fresh fish', 12.50, '/fish.png', 1)");
        jdbc.execute("create table setmeal (id bigint primary key, name varchar(32), price decimal(10,2), "
                + "image varchar(255), status int)");
        jdbc.update("insert into setmeal values (21, 'Fresh meal', 30.00, '/meal.png', 1)");
        jdbc.execute("create table shopping_cart (id bigint generated by default as identity primary key, "
                + "user_id bigint, name varchar(32), dish_id bigint, setmeal_id bigint, dish_flavor varchar(255), "
                + "number int, amount decimal(10,2), image varchar(255), create_time timestamp)");
        jdbc.update("insert into shopping_cart (user_id, name, dish_id, dish_flavor, number, amount, image) values "
                + "(7, 'Fish', 11, 'Spicy', 3, 8.00, '/stale.png'), "
                + "(7, 'Fish', 11, 'Mild', 1, 8.00, '/stale.png'), "
                + "(8, 'Fish', 11, 'Spicy', 8, 8.00, '/stale.png')");

        Configuration configuration = new Configuration(new Environment("test",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeAliasRegistry().registerAliases("com.sky.entity");
        PageInterceptor pagination = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "h2");
        pagination.setProperties(properties);
        configuration.addInterceptor(pagination);
        for (String resource : new String[]{"mapper/OrderMapper.xml", "mapper/OrderDetailMapper.xml",
                "mapper/ShoppingCartMapper.xml", "mapper/DishMapper.xml", "mapper/SetmealMapper.xml"}) {
            try (InputStream xml = getClass().getResourceAsStream("/" + resource)) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(service, "orderDetailMapper", session.getMapper(OrderDetailMapper.class));
        ReflectionTestUtils.setField(service, "shoppingCartMapper", session.getMapper(ShoppingCartMapper.class));
        ReflectionTestUtils.setField(service, "dishMapper", session.getMapper(DishMapper.class));
        ReflectionTestUtils.setField(service, "setmealMapper", session.getMapper(SetmealMapper.class));
        ReflectionTestUtils.setField(service, "weChatPayUtil", weChatPayUtil);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", proxy.getProxy());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper())).build();
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"status\":\"SUCCESS\"}");
    }

    @AfterEach
    void tearDown() throws Exception {
        BaseContext.removeCurrentId();
        PageHelper.clearPage();
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void historyIsScopedToCurrentUserAndSortedByTimeThenId() throws Exception {
        mvc.perform(get("/user/order/historyOrders").param("page", "1").param("pageSize", "2")
                        .param("userId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(44))
                .andExpect(jsonPath("$.data.records[1].id").value(43))
                .andExpect(jsonPath("$.data.records[1].orderDetailList").isEmpty());
        mvc.perform(get("/user/order/historyOrders").param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(42))
                .andExpect(jsonPath("$.data.records[0].orderTime").value("2026-10-15 12:00"))
                .andExpect(jsonPath("$.data.records[0].orderDetailList.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].orderDetailList[0].name").value("Fish"));
    }

    @ParameterizedTest
    @CsvSource({"2,1", "5,1", "6,0", "0,3", ",3"})
    void historyFiltersStatusOrReturnsAllStatuses(Integer orderStatus, int total) throws Exception {
        mvc.perform(get("/user/order/historyOrders").param("page", "1").param("pageSize", "10")
                        .param("status", orderStatus == null ? "" : orderStatus.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(total))
                .andExpect(jsonPath("$.data.records.length()").value(total));
    }

    @Test
    void historyReturnsEmptyPageWithoutLosingTotalAndHandlesNewUser() throws Exception {
        mvc.perform(get("/user/order/historyOrders").param("page", "4").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records").isEmpty());
        BaseContext.setCurrentId(100L);
        mvc.perform(get("/user/order/historyOrders").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,10", "-1,10", "1,0"})
    void historyRejectsInvalidPagination(int page, int pageSize) throws Exception {
        mvc.perform(get("/user/order/historyOrders").param("page", String.valueOf(page))
                        .param("pageSize", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void detailsReturnsSavedOrderAndItemSnapshot() throws Exception {
        action("GET", "orderDetail/42")
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.address").value("Saved address"))
                .andExpect(jsonPath("$.data.consignee").value("Alice"))
                .andExpect(jsonPath("$.data.amount").value(39.97))
                .andExpect(jsonPath("$.data.orderDetailList.length()").value(2))
                .andExpect(jsonPath("$.data.orderDetailList[0].amount").value(9.99))
                .andExpect(jsonPath("$.data.orderDetailList[0].dishFlavor").value("Spicy"))
                .andExpect(jsonPath("$.data.orderDetailList[1].setmealId").value(21));
    }

    @ParameterizedTest
    @CsvSource({"GET,orderDetail/99", "GET,orderDetail/999", "PUT,cancel/99", "PUT,cancel/999",
            "POST,repetition/99", "POST,repetition/999"})
    void rejectsOtherUsersAndMissingOrdersWithoutSideEffects(String method, String path) throws Exception {
        List<Map<String, Object>> orders = jdbc.queryForList("select * from orders order by id");
        List<Map<String, Object>> cart = cart();
        action(method, path).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_NOT_FOUND));
        assertEquals(orders, jdbc.queryForList("select * from orders order by id"));
        assertEquals(cart, cart());
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @CsvSource({"GET,historyOrders?page=1&pageSize=10", "GET,orderDetail/42", "PUT,cancel/42",
            "POST,repetition/42"})
    void rejectsUnauthenticatedRequests(String method, String path) throws Exception {
        BaseContext.removeCurrentId();
        action(method, path).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.USER_NOT_LOGIN));
        verifyNoInteractions(weChatPayUtil);
    }

    @Test
    void cancelsUnpaidOrderWithoutRefund() throws Exception {
        action("PUT", "cancel/44").andExpect(jsonPath("$.code").value(1));
        Map<String, Object> order = jdbc.queryForMap("select * from orders where id=44");
        assertEquals(6, order.get("status"));
        assertEquals(0, order.get("pay_status"));
        assertNotNull(order.get("cancel_reason"));
        assertNotNull(order.get("cancel_time"));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "PROCESSING"})
    void cancelsPaidWaitingOrderAndRefundsOnlyOnce(String refundStatus) throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"status\":\"" + refundStatus + "\"}");
        action("PUT", "cancel/42").andExpect(jsonPath("$.code").value(1));
        Map<String, Object> cancelled = order();
        assertEquals(6, cancelled.get("status"));
        assertEquals(2, cancelled.get("pay_status"));
        assertNotNull(cancelled.get("cancel_time"));
        action("PUT", "cancel/42").andExpect(jsonPath("$.code").value(0));
        assertEquals(cancelled, order());
        verify(weChatPayUtil).refund("order-42", "order-42", new BigDecimal("0.01"), new BigDecimal("0.01"));
    }

    @ParameterizedTest
    @CsvSource({"3,1", "4,1", "5,1", "6,2", "1,1", "2,0", "2,2"})
    void cancellationRejectsAcceptedAndTerminalOrInconsistentStates(int orderStatus, int payStatus) throws Exception {
        jdbc.update("update orders set status=?, pay_status=? where id=42", orderStatus, payStatus);
        Map<String, Object> original = order();
        action("PUT", "cancel/42").andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
        assertEquals(original, order());
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"ABNORMAL\"}", "{\"status\":\"CLOSED\"}", "null"})
    void refundFailureRollsBackCancellation(String response) throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenReturn(response);
        Map<String, Object> original = order();
        action("PUT", "cancel/42").andExpect(jsonPath("$.code").value(0));
        assertEquals(original, order());
    }

    @Test
    void checkedRefundExceptionAlsoRollsBackCancellation() throws Exception {
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenThrow(new IOException("gateway down"));
        Map<String, Object> original = order();
        assertThrows(IOException.class, () -> action("PUT", "cancel/42"));
        assertEquals(original, order());
    }

    @Test
    void concurrentCancellationsRefundOnlyOnce() throws Exception {
        CountDownLatch refundStarted = new CountDownLatch(1);
        CountDownLatch finishRefund = new CountDownLatch(1);
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            refundStarted.countDown();
            assertTrue(finishRefund.await(5, TimeUnit.SECONDS));
            return "{\"status\":\"SUCCESS\"}";
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResultActions> first = executor.submit(this::cancelAsCurrentUser);
            assertTrue(refundStarted.await(5, TimeUnit.SECONDS));
            Future<ResultActions> second = executor.submit(this::cancelAsCurrentUser);
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            finishRefund.countDown();
            first.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(1));
            second.get(5, TimeUnit.SECONDS).andExpect(jsonPath("$.code").value(0));
            assertEquals(6, order().get("status"));
            verify(weChatPayUtil, times(1)).refund(anyString(), anyString(), any(), any());
        } finally {
            finishRefund.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void repetitionMergesOnlyMatchingProductsAndUsesCurrentPricesWithoutChangingHistory() throws Exception {
        Map<String, Object> original = order();
        List<Map<String, Object>> items = jdbc.queryForList("select * from order_detail order by id");
        action("POST", "repetition/42").andExpect(jsonPath("$.code").value(1));
        assertEquals(4, cart().size());
        Map<String, Object> fish = jdbc.queryForMap("select * from shopping_cart "
                + "where user_id=7 and dish_id=11 and dish_flavor='Spicy'");
        assertEquals(5, fish.get("number"));
        assertEquals("Fresh fish", fish.get("name"));
        assertEquals(new BigDecimal("12.50"), fish.get("amount"));
        assertEquals("/fish.png", fish.get("image"));
        Map<String, Object> meal = jdbc.queryForMap("select * from shopping_cart where user_id=7 and setmeal_id=21");
        assertEquals(1, meal.get("number"));
        assertEquals("Fresh meal", meal.get("name"));
        assertEquals(new BigDecimal("30.00"), meal.get("amount"));
        assertNotNull(meal.get("create_time"));
        assertEquals(1, jdbc.queryForObject("select number from shopping_cart where user_id=7 and dish_flavor='Mild'",
                Integer.class));
        assertEquals(8, jdbc.queryForObject("select number from shopping_cart where user_id=8", Integer.class));
        assertEquals(original, order());
        assertEquals(items, jdbc.queryForList("select * from order_detail order by id"));
    }

    @Test
    void repetitionIntoEmptyCartPreservesQuantitiesAndFlavors() throws Exception {
        jdbc.update("delete from shopping_cart where user_id=7");
        action("POST", "repetition/42").andExpect(jsonPath("$.code").value(1));
        assertEquals(2, jdbc.queryForObject("select count(*) from shopping_cart where user_id=7", Integer.class));
        assertEquals(2, jdbc.queryForObject("select number from shopping_cart where user_id=7 and dish_id=11", Integer.class));
        assertEquals("Spicy", jdbc.queryForObject("select dish_flavor from shopping_cart where user_id=7 and dish_id=11",
                String.class));
    }

    @ParameterizedTest
    @CsvSource({"dish,11,Fish,false", "dish,11,Fish,true", "setmeal,21,Meal,false", "setmeal,21,Meal,true"})
    void repetitionRejectsDeletedOrStoppedProductsAndRollsBackEntireCart(String table, long id,
                                                                       String name, boolean deleted)
            throws Exception {
        if (deleted) {
            jdbc.update("delete from " + table + " where id=?", id);
        } else {
            jdbc.update("update " + table + " set status=0 where id=?", id);
        }
        List<Map<String, Object>> original = cart();
        action("POST", "repetition/42").andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(containsString(name)));
        assertEquals(original, cart());
    }

    @Test
    void repetitionRollsBackNewCartEntriesWhenLaterProductIsUnavailable() throws Exception {
        jdbc.update("delete from shopping_cart where user_id=7");
        jdbc.update("delete from setmeal where id=21");
        List<Map<String, Object>> original = cart();
        action("POST", "repetition/42").andExpect(jsonPath("$.code").value(0));
        assertEquals(original, cart());
    }

    @Test
    void repetitionRejectsEmptyOrderWithoutChangingCart() throws Exception {
        List<Map<String, Object>> original = cart();
        action("POST", "repetition/43").andExpect(jsonPath("$.code").value(0));
        assertEquals(original, cart());
    }

    private ResultActions action(String method, String path) throws Exception {
        return mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/user/order/" + path))
                .andExpect(status().isOk());
    }

    private ResultActions cancelAsCurrentUser() throws Exception {
        BaseContext.setCurrentId(7L);
        try {
            return action("PUT", "cancel/42");
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    private Map<String, Object> order() {
        return jdbc.queryForMap("select * from orders where id=42");
    }

    private List<Map<String, Object>> cart() {
        return jdbc.queryForList("select * from shopping_cart order by id");
    }
}
