package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 使用当前登录用户的 ID 清空购物车，不影响其他用户的数据。
     */
    @Override
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 使用当前登录用户的 ID 查询购物车，避免访问其他用户的数据。
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        return shoppingCartMapper.listByUserId(BaseContext.getCurrentId());
    }

    /**
     * 添加购物车：商品已存在时数量加一，否则补全商品信息并插入。
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 使用当前登录用户的 ID，避免查询到其他用户的购物车。
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .build();
        // 按当前用户、菜品及口味或套餐查询已有的购物车记录。
        ShoppingCart existing = shoppingCartMapper.getByProduct(shoppingCart);
        if (existing != null) {
            // 商品已存在，直接在数据库中将数量加一，无需新增记录。
            shoppingCartMapper.incrementNumber(existing.getId());
            return;
        }

        // 商品不存在：根据菜品 ID 是否为空，判断本次添加的是菜品还是套餐。
        if (shoppingCart.getDishId() != null) {
            // 添加菜品，从菜品表获取名称、单价和图片。
            Dish dish = dishMapper.getById(shoppingCart.getDishId());
            shoppingCart.setName(dish.getName());
            shoppingCart.setAmount(dish.getPrice());
            shoppingCart.setImage(dish.getImage());
        } else {
            // 添加套餐，从套餐表获取名称、单价和图片。
            Setmeal setmeal = setmealMapper.getById(shoppingCart.getSetmealId());
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setAmount(setmeal.getPrice());
            shoppingCart.setImage(setmeal.getImage());
        }
        // 首次添加时数量为 1，记录创建时间后插入购物车。
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());
        shoppingCartMapper.insert(shoppingCart);
    }
}
