package com.sky.controller.admin;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private Connection connection;
    private SqlSession session;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:order-search;MODE=MySQL", "sa", "");
        connection = dataSource.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table orders (id bigint primary key, number varchar(50), status int, "
                    + "phone varchar(11), order_time timestamp, amount decimal(10, 2))");
            statement.execute("create table order_detail (id bigint primary key, order_id bigint, name varchar(32), number int)");
            statement.execute("insert into orders values "
                    + "(1, 'DEMO-1001', 2, '00000000001', '2026-10-14 10:00:00', 12), "
                    + "(2, 'DEMO-1002', 2, '00000000002', '2026-10-14 11:00:00', 20), "
                    + "(3, 'DEMO-1003', 2, '00000000003', '2026-10-14 11:00:00', 30), "
                    + "(4, 'DEMO-1004', 3, '00000000004', '2026-10-14 12:00:00', 40), "
                    + "(5, 'OTHER-1005', 5, '00000000005', '2026-10-13 10:00:00', 50), "
                    + "(6, 'DEMO-1006', 6, '00000000006', '2026-10-14 13:00:00', 60)");
            statement.execute("insert into order_detail values "
                    + "(1, 3, 'Fish', 2), (2, 3, 'Rice', 1), (3, 2, 'Soup', 1)");
        }

        Configuration configuration = new Configuration(new Environment("test",
                new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        PageInterceptor pagination = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "h2");
        pagination.setProperties(properties);
        configuration.addInterceptor(pagination);
        for (String resource : new String[]{"mapper/OrderMapper.xml", "mapper/OrderDetailMapper.xml"}) {
            try (InputStream xml = getClass().getResourceAsStream("/" + resource)) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(service, "orderDetailMapper", session.getMapper(OrderDetailMapper.class));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper())).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        PageHelper.clearPage();
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void returnsPendingOrdersWithTotalAndDishSummaryInStableOrder() throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "1").param("pageSize", "2").param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(3))
                .andExpect(jsonPath("$.data.records[0].status").value(2))
                .andExpect(jsonPath("$.data.records[0].amount").value(30))
                .andExpect(jsonPath("$.data.records[0].orderTime").value("2026-10-14 11:00"))
                .andExpect(jsonPath("$.data.records[0].orderDishes").value("Fish*2;Rice*1"))
                .andExpect(jsonPath("$.data.records[1].id").value(2))
                .andExpect(jsonPath("$.data.records[1].orderDishes").value("Soup*1"));
    }

    @Test
    void returnsSecondPageAndEmptySummaryForOrderWithoutDetails() throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "2").param("pageSize", "2").param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].orderDishes").value(""));
    }

    @Test
    void returnsEmptyPageWithoutLosingMatchingTotal() throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "3").param("pageSize", "2").param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?status=0"})
    void returnsAllStatusesWhenStatusIsAbsentOrZero(String query) throws Exception {
        mvc.perform(get("/admin/order/conditionSearch" + query)
                        .param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.records.length()").value(6));
    }

    @ParameterizedTest
    @CsvSource({"1002,,2", ",00000000003,3"})
    void filtersByPartialOrderNumberOrPhone(String number, String phone, int id) throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "1").param("pageSize", "10")
                        .param("number", number == null ? "" : number)
                        .param("phone", phone == null ? "" : phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id));
    }

    @Test
    void filtersByInclusiveTimeRangeAndStatusTogether() throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "1").param("pageSize", "10").param("status", "2")
                        .param("beginTime", "2026-10-14 11:00:00")
                        .param("endTime", "2026-10-14 12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(3))
                .andExpect(jsonPath("$.data.records[1].id").value(2));
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "1").param("pageSize", "10")
                        .param("beginTime", "2026-10-14 11:00:00")
                        .param("endTime", "2026-10-14 12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(4));
    }

    @Test
    void returnsZeroTotalAndEmptyRecordsWhenNoOrdersMatch() throws Exception {
        mvc.perform(get("/admin/order/conditionSearch")
                        .param("page", "1").param("pageSize", "10").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void returnsCountsForTheOrderStatusTabs() throws Exception {
        mvc.perform(get("/admin/order/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.toBeConfirmed").value(3))
                .andExpect(jsonPath("$.data.confirmed").value(1))
                .andExpect(jsonPath("$.data.deliveryInProgress").value(0));
    }

    @Test
    void returnsZeroStatusCountsWhenNoOrdersExist() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("delete from orders");
        }
        mvc.perform(get("/admin/order/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toBeConfirmed").value(0))
                .andExpect(jsonPath("$.data.confirmed").value(0))
                .andExpect(jsonPath("$.data.deliveryInProgress").value(0));
    }
}
