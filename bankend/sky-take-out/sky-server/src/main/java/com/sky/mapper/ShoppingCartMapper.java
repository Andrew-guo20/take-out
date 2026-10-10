package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    /**
     * 根据购物车记录 ID 将数量减一，数量不得小于 1。
     */
    @Update("update shopping_cart set number = number - 1 where id = #{id} and number > 1")
    void decrementNumber(Long id);

    /**
     * 删除只剩一份的购物车记录。
     */
    @Delete("delete from shopping_cart where id = #{id} and number = 1")
    void deleteById(Long id);

    /**
     * 删除指定用户的全部购物车记录。
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 查询指定用户的购物车，按创建时间倒序返回。
     */
    @Select("select * from shopping_cart where user_id = #{userId} order by create_time desc, id desc")
    List<ShoppingCart> listByUserId(Long userId);

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
