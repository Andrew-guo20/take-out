package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    /**
     * 商品数量减一，数量为 1 时删除购物车记录。
     * @param shoppingCartDTO 商品 ID 和菜品口味
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空当前登录用户的全部购物车记录。
     */
    void cleanShoppingCart();

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
