package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderMapperTurnoverTest {

    @Test
    void includesMidnightOrderAndExcludesNextDayAndUncompletedOrders() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:turnover;MODE=MySQL", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table orders (amount decimal(10, 2), order_time timestamp(6), status int)");
                statement.execute("insert into orders values "
                        + "(10.10, '2026-10-13 00:00:00', 5), "
                        + "(20.20, '2026-10-13 23:59:59.999999', 5), "
                        + "(40.40, '2026-10-12 23:59:59', 5), "
                        + "(80.80, '2026-10-14 00:00:00', 5), "
                        + "(160.60, '2026-10-13 12:00:00', 6)");
            }

            Configuration configuration = new Configuration(new Environment("test",
                    new JdbcTransactionFactory(), dataSource));
            try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/OrderMapper.xml")) {
                new XMLMapperBuilder(mapperXml, configuration, "mapper/OrderMapper.xml",
                        configuration.getSqlFragments()).parse();
            }
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
            LocalDate date = LocalDate.of(2026, 10, 13);
            try (SqlSession session = factory.openSession()) {
                Map<String, Object> params = new HashMap<>();
                params.put("begin", date.atStartOfDay());
                params.put("end", date.plusDays(1).atStartOfDay());
                params.put("status", Orders.COMPLETED);
                assertEquals(30.30, session.getMapper(OrderMapper.class).sumByMap(params), 0.000001);
            }
        }
    }
}
