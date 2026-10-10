package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShoppingCartMapper {
    /**
     * 查询当前用户购物车中相同菜品及口味或相同套餐的记录。
     */
    ShoppingCart getByProduct(ShoppingCart shoppingCart);

    /**
     * 根据购物车记录 ID 将数量加一。
     */
    @Update("update shopping_cart set number = number + 1 where id = #{id}")
    void incrementNumber(Long id);

    /**
     * 插入一条新的购物车记录，amount 保存商品单价。
     */
    @Insert("insert into shopping_cart " +
            "(name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time) " +
            "values (#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, " +
            "#{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart shoppingCart);
}
