package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement //开启注解方式的事务管理
@Slf4j
public class SkyApplication {
    public static void main(String[] args) {
        //让 Druid 校验空闲连接时改用 SELECT 1 而不是 MySQL 的 ping 方法，
        //避免日志打印 "discard long time none received connection"
        System.setProperty("druid.mysql.usePingMethod", "false");
        SpringApplication.run(SkyApplication.class, args);
        log.info("server started");
    }
}
