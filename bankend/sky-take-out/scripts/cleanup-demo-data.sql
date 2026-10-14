-- Removes only the demonstration batch created by SeedDemoData.java.
USE sky_take_out;
START TRANSACTION;

DELETE d FROM order_detail d
JOIN orders o ON o.id = d.order_id
WHERE o.number LIKE 'DEMO20261014-%';

DELETE FROM orders WHERE number LIKE 'DEMO20261014-%';

DELETE a FROM address_book a
JOIN user u ON u.id = a.user_id
WHERE u.openid LIKE 'demo-20261014-%';

DELETE FROM user WHERE openid LIKE 'demo-20261014-%';

COMMIT;
