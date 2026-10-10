package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    /**
     * 查询当前登录用户的全部购物车记录。
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 添加菜品或套餐到当前用户的购物车。
     * @param shoppingCartDTO 商品 ID 和菜品口味
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
