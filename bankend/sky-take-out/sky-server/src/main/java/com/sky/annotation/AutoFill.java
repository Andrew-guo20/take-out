package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注释，用于标识某个方法需要进行功能字段自动填充处理
 * Target 和 Retention 本身也是注解，叫做“元注解”，用于描述自定义注解应该如何使用
 * Target 用来限制 @AutoFill 可以标注在哪些位置
 * ElementType.METHOD      // 方法
 * ElementType.TYPE       // 类、接口、枚举
 * ElementType.FIELD      // 成员变量
 * ElementType.PARAMETER  // 方法参数
 * ElementType.CONSTRUCTOR // 构造方法
 * Retention 用来规定注解保留到什么时候
 * RetentionPolicy.SOURCE 只在源代码中存在，编译后被丢弃。例如 @Override。
 * RetentionPolicy.CLASS  会保留在 .class 文件中，但程序运行时不能通过反射读取。这是默认值
 * RetentionPolicy.RUNTIME  会一直保留到程序运行时，可以通过反射或 Spring AOP 读取
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
// @interface 表示声明自定义注解，用于给类、方法或字段添加元数据
public @interface AutoFill {
    // 表示使用 @AutoFill 必须指定数据库操作类型：UPDATE INSERT
    // 切面根据不同值执行不同逻辑
    OperationType value();
}
