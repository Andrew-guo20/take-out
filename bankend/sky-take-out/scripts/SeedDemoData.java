import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

// Run from the repository root with the project's SnakeYAML and MySQL JDBC jars on the classpath.
public class SeedDemoData {
    private static final String USER_PREFIX = "demo-20261014-";
    private static final String ORDER_PREFIX = "DEMO20261014-";

    record Customer(long id, long addressId, String name, String phone, String address) {}
    record Goods(Long dishId, Long setmealId, String name, BigDecimal price, String image) {}
    record Line(Goods goods, int quantity) {}

    public static void main(String[] args) throws Exception {
        Map<?, ?> config;
        try (var reader = Files.newBufferedReader(Path.of("sky-server/src/main/resources/application-dev.yml"))) {
            config = new Yaml().load(reader);
        }
        var db = (Map<?, ?>) ((Map<?, ?>) config.get("sky")).get("datasource");
        String url = "jdbc:mysql://" + db.get("host") + ":" + db.get("port") + "/" + db.get("database")
                + "?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8"
                + "&useSSL=false&allowPublicKeyRetrieval=true";

        try (var connection = DriverManager.getConnection(url,
                db.get("username").toString(), db.get("password").toString())) {
            if (count(connection, "select count(*) from user where openid like '" + USER_PREFIX + "%'") != 0
                    || count(connection, "select count(*) from orders where number like '" + ORDER_PREFIX + "%'") != 0) {
                throw new IllegalStateException("This demo batch already exists; no data was changed.");
            }
            var menu = new ArrayList<Goods>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select id, name, price, image from dish where status = 1 "
                            + "order by case when price between 18 and 88 then 0 else 1 end, id")) {
                while (rows.next()) {
                    menu.add(new Goods(rows.getLong("id"), null, rows.getString("name"),
                            rows.getBigDecimal("price"), rows.getString("image")));
                }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select id, name, price, image from setmeal where status = 1 order by id")) {
                while (rows.next()) {
                    menu.add(new Goods(null, rows.getLong("id"), rows.getString("name"),
                            rows.getBigDecimal("price"), rows.getString("image")));
                }
            }
            if (menu.size() < 4) {
                throw new IllegalStateException("At least four available goods are needed for the demo.");
            }

            var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            LocalDate today = now.toLocalDate();
            LocalDate begin = today.minusDays(29);
            var random = new Random(20261014);
            var registrationRandom = new Random(20261015);
            var customers = new ArrayList<Customer>();
            int orderCount = 0;
            int detailCount = 0;
            connection.setAutoCommit(false);
            try {
                for (int index = 0; index < 120; index++) {
                    String name = String.format("演示用户%03d", index + 1);
                    String phone = String.format("000%08d", index + 1);
                    int registrationDay = Math.max(0, index / 4 - registrationRandom.nextInt(3));
                    long userId = insert(connection,
                            "insert into user (openid, name, phone, sex, create_time) values (?, ?, ?, ?, ?)",
                            USER_PREFIX + String.format("u%03d", index + 1), name, phone,
                            String.valueOf(index % 2), begin.plusDays(registrationDay).atStartOfDay());
                    String detail = "演示路" + (index % 12 + 1) + "号" + (index + 101) + "室";
                    long addressId = insert(connection,
                            "insert into address_book (user_id, consignee, sex, phone, province_code, province_name, "
                                    + "city_code, city_name, district_code, district_name, detail, label, is_default) "
                                    + "values (?, ?, ?, ?, '310000', '上海市', '310100', '上海市', '310115', '浦东新区', ?, '家', 1)",
                            userId, name, String.valueOf(index % 2), phone, detail);
                    customers.add(new Customer(userId, addressId, name, phone, "上海市浦东新区" + detail));
                }

                for (int day = 0; day < 30; day++) {
                    LocalDate date = begin.plusDays(day);
                    boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                            || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                    int dailyCount = 18 + day / 2 + (weekend ? 8 : 0) + random.nextInt(11);
                    int latestSecond = date.equals(today) ? now.toLocalTime().toSecondOfDay() - 3600 : 21 * 3600;
                    if (latestSecond <= 0) {
                        continue;
                    }
                    for (int index = 0; index < dailyCount; index++) {
                        Customer customer = customers.get(random.nextInt((day + 1) * 4));
                        int earliestSecond = Math.min(10 * 3600, latestSecond / 2);
                        LocalDateTime orderTime = date.atStartOfDay().plusSeconds(
                                earliestSecond + random.nextInt(latestSecond - earliestSecond));
                        int status = random.nextInt(100) < 88 ? 5 : 6;
                        if (date.equals(today) && index < 6) {
                            status = 2 + index % 3;
                            orderTime = now.minusMinutes(20 - index * 2);
                        }
                        var lines = new ArrayList<Line>();
                        var selected = new HashSet<Integer>();
                        int lineCount = 2 + random.nextInt(3);
                        BigDecimal amount = new BigDecimal("2.00");
                        int quantity = 0;
                        while (lines.size() < lineCount) {
                            // Favor a few popular dishes while retaining sales for every menu item.
                            int goodsIndex = (int) (Math.pow(random.nextDouble(), 1.8) * menu.size());
                            if (!selected.add(goodsIndex)) {
                                continue;
                            }
                            Goods goods = menu.get(goodsIndex);
                            int number = random.nextInt(4) == 0 ? 2 : 1;
                            lines.add(new Line(goods, number));
                            quantity += number;
                            amount = amount.add(goods.price().multiply(BigDecimal.valueOf(number)));
                        }
                        long orderId = insert(connection,
                                "insert into orders (number, status, user_id, address_book_id, order_time, checkout_time, "
                                        + "pay_method, pay_status, amount, remark, phone, address, user_name, consignee, "
                                        + "cancel_reason, cancel_time, estimated_delivery_time, delivery_status, "
                                        + "delivery_time, pack_amount, tableware_number, tableware_status) "
                                        + "values (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 2, ?, 1)",
                                ORDER_PREFIX + String.format("%04d", ++orderCount), status, customer.id(),
                                customer.addressId(), orderTime, status == 6 ? null : orderTime.plusMinutes(2),
                                status == 6 ? 0 : 1, amount, "演示数据20261014", customer.phone(), customer.address(),
                                customer.name(), customer.name(), status == 6 ? "演示：用户取消订单" : null,
                                status == 6 ? orderTime.plusMinutes(5) : null, orderTime.plusMinutes(45),
                                status == 5 ? orderTime.plusMinutes(30) : null, quantity);
                        for (Line line : lines) {
                            Goods goods = line.goods();
                            insert(connection,
                                    "insert into order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount) "
                                            + "values (?, ?, ?, ?, ?, '', ?, ?)",
                                    goods.name(), goods.image(), orderId, goods.dishId(), goods.setmealId(),
                                    line.quantity(), goods.price());
                            detailCount++;
                        }
                    }
                }

                if (count(connection, "select count(*) from user where openid like '" + USER_PREFIX + "%'") != 120
                        || count(connection, "select count(*) from orders where number like '" + ORDER_PREFIX + "%'") != orderCount
                        || count(connection, "select count(*) from order_detail d join orders o on d.order_id = o.id "
                                + "where o.number like '" + ORDER_PREFIX + "%'") != detailCount
                        || count(connection, "select count(*) from orders o left join user u on u.id = o.user_id "
                                + "left join address_book a on a.id = o.address_book_id where o.number like '" + ORDER_PREFIX
                                + "%' and (u.id is null or a.id is null or a.user_id <> o.user_id or u.create_time > o.order_time "
                                + "or o.amount <> o.pack_amount + (select sum(d.amount * d.number) from order_detail d where d.order_id = o.id))") != 0) {
                    throw new IllegalStateException("Demo row counts, relationships or amounts did not match.");
                }
                connection.commit();
                System.out.printf("Inserted: 120 users, 120 addresses, %d orders, %d order details.%n", orderCount, detailCount);
                System.out.printf("Date range: %s through %s; order prefix: %s%n", begin, today, ORDER_PREFIX);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
