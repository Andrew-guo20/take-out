package com.sky.mapper;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperStatisticsTest {

    @Test
    void countsRegistrationsAtMidnightAndIncludesEarlierUsersInCumulativeCount() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:user-statistics;MODE=MySQL;NON_KEYWORDS=USER", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table user (id bigint primary key, create_time timestamp(6))");
                statement.execute("insert into user values "
                        + "(1, '2026-10-09 12:00:00'), "
                        + "(2, '2026-10-10 23:59:59.999999'), "
                        + "(3, '2026-10-11 00:00:00'), "
                        + "(4, '2026-10-11 23:59:59.999999'), "
                        + "(5, '2026-10-13 00:00:00'), "
                        + "(6, '2026-10-13 23:59:59.999999'), "
                        + "(7, '2026-10-14 00:00:00')");
            }

            Configuration configuration = new Configuration(new Environment("test",
                    new JdbcTransactionFactory(), dataSource));
            configuration.getTypeAliasRegistry().registerAlias("User", com.sky.entity.User.class);
            try (InputStream mapperXml = getClass().getResourceAsStream("/mapper/UserMapper.xml")) {
                new XMLMapperBuilder(mapperXml, configuration, "mapper/UserMapper.xml",
                        configuration.getSqlFragments()).parse();
            }
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
            LocalDate begin = LocalDate.of(2026, 10, 11);
            try (SqlSession session = factory.openSession()) {
                UserMapper mapper = session.getMapper(UserMapper.class);
                assertEquals(2, mapper.countByMap(begin.atStartOfDay(), begin.plusDays(1).atStartOfDay()));
                assertEquals(4, mapper.countByMap(null, begin.plusDays(1).atStartOfDay()));
                assertEquals(0, mapper.countByMap(begin.plusDays(1).atStartOfDay(), begin.plusDays(2).atStartOfDay()));
                assertEquals(4, mapper.countByMap(null, begin.plusDays(2).atStartOfDay()));
                assertEquals(2, mapper.countByMap(begin.plusDays(2).atStartOfDay(), begin.plusDays(3).atStartOfDay()));
                assertEquals(6, mapper.countByMap(null, begin.plusDays(3).atStartOfDay()));
            }
        }
    }
}
