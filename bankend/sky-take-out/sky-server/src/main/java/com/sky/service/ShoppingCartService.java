package com.sky.service;

import com.sky.dto.ShoppingCartDTO;

public interface ShoppingCartService {
    /**
     * 添加菜品或套餐到当前用户的购物车。
     * @param shoppingCartDTO 商品 ID 和菜品口味
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
