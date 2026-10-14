package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDetailMapperTop10Test {

    @Test
    void returnsTopSellingCompletedOrderDetailsWithinWholeDay() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:top10;MODE=MySQL", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table orders (id bigint primary key, order_time timestamp(6), status int)");
                statement.execute("create table order_detail (id bigint primary key, name varchar(50), order_id bigint, number int)");
                statement.execute("insert into orders values "
                        + "(1, '2026-10-13 00:00:00', 5), "
                        + "(2, '2026-10-13 23:59:59.999999', 5), "
                        + "(3, '2026-10-12 18:00:00', 5), "
                        + "(4, '2026-10-13 08:00:00', 6), "
                        + "(5, '2026-10-14 00:00:00', 5), "
                        + "(6, '2026-10-15 12:00:00', 5)");
                statement.execute("insert into order_detail values "
                        + "(1, '鱼香肉丝', 1, 2), "
                        + "(2, '宫保鸡丁', 1, 1), "
                        + "(3, '鱼香肉丝', 2, 3), "
                        + "(4, '水煮鱼', 3, 10), "
                        + "(5, '宫保鸡丁', 4, 7), "
                        + "(6, '鱼香肉丝', 5, 99)");
                for (int item = 12; item >= 1; item--) {
                    int number = item <= 8 ? 30 - item : 10;
                    statement.execute("insert into order_detail values (" + (item + 6)
                            + ", '" + String.format("Item%02d", item) + "', 6, " + number + ")");
                }
            }

            Configuration configuration = new Configuration(new Environment("test",
                    new JdbcTransactionFactory(), dataSource));
            try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/OrderDetailMapper.xml")) {
                new XMLMapperBuilder(mapperXml, configuration, "mapper/OrderDetailMapper.xml",
                        configuration.getSqlFragments()).parse();
            }
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
            LocalDate date = LocalDate.of(2026, 10, 13);

            try (SqlSession session = factory.openSession()) {
                OrderDetailMapper mapper = session.getMapper(OrderDetailMapper.class);
                List<GoodsSalesDTO> result = mapper.getSalesTop10(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay(), Orders.COMPLETED);

                assertEquals(2, result.size());
                assertEquals("鱼香肉丝", result.get(0).getName());
                assertEquals(5, result.get(0).getNumber());
                assertEquals("宫保鸡丁", result.get(1).getName());
                assertEquals(1, result.get(1).getNumber());

                LocalDate rankingDate = LocalDate.of(2026, 10, 15);
                List<GoodsSalesDTO> top10 = mapper.getSalesTop10(
                        rankingDate.atStartOfDay(), rankingDate.plusDays(1).atStartOfDay(), Orders.COMPLETED);

                assertEquals(10, top10.size());
                for (int item = 1; item <= 10; item++) {
                    assertEquals(String.format("Item%02d", item), top10.get(item - 1).getName());
                    assertEquals(item <= 8 ? 30 - item : 10, top10.get(item - 1).getNumber());
                }
                assertTrue(mapper.getSalesTop10(
                        rankingDate.plusDays(1).atStartOfDay(), rankingDate.plusDays(2).atStartOfDay(),
                        Orders.COMPLETED).isEmpty());
            }
        }
    }
}
