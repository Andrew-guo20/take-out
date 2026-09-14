package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
// Lombok，一个Java类库，提供了一组注解，简化POJ0实体类开发
// @Data为当前实体类在编译期设置对应的get/set方法，无参/无参构造方法，toString方法，hashCode方法，equals方法等

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    private String username;

    private String name;

    private String phone;

    private String sex;

    private String idNumber;

}
