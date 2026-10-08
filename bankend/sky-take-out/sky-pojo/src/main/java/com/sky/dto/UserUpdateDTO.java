package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserUpdateDTO implements Serializable {

    //姓名
    private String name;

    //头像
    private String avatar;
}
