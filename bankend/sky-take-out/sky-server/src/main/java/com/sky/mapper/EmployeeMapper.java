package com.sky.mapper;

import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    // 告诉 MyBatis，这个方法执行括号中的查询语句
    // sql比较简短用逐渐，复杂用yaml
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

}
